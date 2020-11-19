/*
 * Copyright 2010-2020 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.caching

import org.jetbrains.kotlin.cli.common.arguments.CommonToolArguments
import org.jetbrains.kotlin.gradle.getMethodOrNull
import java.lang.reflect.Method
import java.lang.reflect.Proxy
import kotlin.reflect.KProperty1
import kotlin.reflect.full.declaredMemberProperties

typealias CachedCompilerArgumentBySourceSet = Map<String, CachedArgsInfo>
typealias FlatCompilerArgumentBySourceSet = Map<String, FlatArgsInfo>

const val ARGUMENT_ANNOTATION_CLASS = "org.jetbrains.kotlin.cli.common.arguments.Argument"
const val PARSE_ARGUMENTS_CLASS = "org.jetbrains.kotlin.cli.common.arguments.ParseCommandLineArgumentsKt"
const val COMMON_TOOL_ARGUMENTS_CLASS = "org.jetbrains.kotlin.cli.common.arguments.CommonToolArguments"
const val JS_DCE_ARGUMENTS_CLASS = "org.jetbrains.kotlin.cli.common.arguments.K2JSDceArguments"
const val COMMON_ARGUMENTS_CLASS = "org.jetbrains.kotlin.cli.common.arguments.CommonCompilerArguments"
const val JS_ARGUMENTS_CLASS = "org.jetbrains.kotlin.cli.common.arguments.K2JSCompilerArguments"
const val METADATA_ARGUMENTS_CLASS = "org.jetbrains.kotlin.cli.common.arguments.K2MetadataCompilerArguments"
const val JVM_ARGUMENTS_CLASS = "org.jetbrains.kotlin.cli.common.arguments.K2JVMCompilerArguments"

data class ArgumentAnnotationInfo(
    val value: String,
    val shortName: String,
    val deprecatedName: String,
    val delimiter: String,
    val isAdvanced: Boolean
)

fun ArgumentAnnotationInfo.isSuitableValue(value: String): Boolean =
    if (isAdvanced) value.startsWith(this.value) || value.startsWith(shortName) || value.startsWith(deprecatedName)
    else value == this.value || value == shortName || value == deprecatedName

data class DividedPropertiesWithArgumentAnnotationInfo(
    val flagPropertiesToArgumentAnnotation: HashMap<KProperty1<*, *>, ArgumentAnnotationInfo>,
    val singlePropertiesToArgumentAnnotation: HashMap<KProperty1<*, *>, ArgumentAnnotationInfo>,
    val multiplePropertiesToArgumentAnnotation: HashMap<KProperty1<*, *>, ArgumentAnnotationInfo>,
    val classpathPropertiesToArgumentAnnotation: HashMap<KProperty1<*, *>, ArgumentAnnotationInfo>
)

private fun getClassSafely(className: String, classLoader: ClassLoader?): Class<*>? = try {
    Class.forName(className, false, classLoader)
} catch (e: NoClassDefFoundError) {
    null
}

val ClassLoader?.argumentAnnotationClazz: Class<*>?
    get() = getClassSafely(ARGUMENT_ANNOTATION_CLASS, this)

val ClassLoader?.commonToolArgumentsClazz: Class<*>?
    get() = getClassSafely(COMMON_TOOL_ARGUMENTS_CLASS, this)
val ClassLoader?.jsDceArgumentsClazz: Class<*>?
    get() = getClassSafely(JS_DCE_ARGUMENTS_CLASS, this)
val ClassLoader?.commonArgumentsClazz: Class<*>?
    get() = getClassSafely(COMMON_ARGUMENTS_CLASS, this)
val ClassLoader?.jsArgumentsClazz: Class<*>?
    get() = getClassSafely(JS_ARGUMENTS_CLASS, this)
val ClassLoader?.metadataArgumentsClazz: Class<*>?
    get() = getClassSafely(METADATA_ARGUMENTS_CLASS, this)
val ClassLoader?.jvmArgumentsClazz: Class<*>?
    get() = getClassSafely(JVM_ARGUMENTS_CLASS, this)

val ClassLoader?.isAdvancedMethod: Method?
    get() = argumentAnnotationClazz?.let { getClassSafely(PARSE_ARGUMENTS_CLASS, this)?.getMethodOrNull("isAdvanced", it) }

class DividedPropertiesWithArgumentAnnotationInfoManager(val classLoader: ClassLoader?) {
    val dividedPropertiesWithArgumentAnnotationInfo: DividedPropertiesWithArgumentAnnotationInfo by lazy {
        val allPropertiesToArgumentAnnotation = with(classLoader) {
            listOfNotNull(
                commonToolArgumentsClazz,
                jsDceArgumentsClazz,
                commonArgumentsClazz,
                jsArgumentsClazz,
                metadataArgumentsClazz,
                jvmArgumentsClazz
            ).flatMap { it.kotlin.declaredMemberProperties }
                .mapNotNull { argClazzProp ->
                    argClazzProp.annotations.firstOrNull { annotation ->
                        with(annotation::class.java) {
                            Proxy.isProxyClass(this)
                                    && (getMethodOrNull("annotationType")?.invoke(annotation) as Class<*>).name == ARGUMENT_ANNOTATION_CLASS
                                    || kotlin == argumentAnnotationClazz?.kotlin
                        }
                    }?.let {
                        val value = it.javaClass.getMethodOrNull("value")?.invoke(it) as? String ?: return@mapNotNull null
                        val shortName = it.javaClass.getMethodOrNull("shortName")?.invoke(it) as? String ?: return@mapNotNull null
                        val deprecatedName = it.javaClass.getMethodOrNull("deprecatedName")?.invoke(it) as? String ?: return@mapNotNull null
                        val delimiter = it.javaClass.getMethodOrNull("delimiter")?.invoke(it) as? String ?: return@mapNotNull null
                        val isAdvanced = isAdvancedMethod?.invoke(null, it) as? Boolean ?: return@mapNotNull null
                        argClazzProp to ArgumentAnnotationInfo(value, shortName, deprecatedName, delimiter, isAdvanced)
                    } ?: return@mapNotNull null

                }.toMap()
        }

        val flagPropertiesToArgumentAnnotation: HashMap<KProperty1<*, *>, ArgumentAnnotationInfo> = hashMapOf()
        val singlePropertiesToArgumentAnnotation: HashMap<KProperty1<*, *>, ArgumentAnnotationInfo> = hashMapOf()
        val multiplePropertiesToArgumentAnnotation: HashMap<KProperty1<*, *>, ArgumentAnnotationInfo> = hashMapOf()
        val classpathPropertiesToArgumentAnnotation: HashMap<KProperty1<*, *>, ArgumentAnnotationInfo> = hashMapOf()

        allPropertiesToArgumentAnnotation.forEach { (prop, ann) ->
            if (prop.name == "classpath") classpathPropertiesToArgumentAnnotation[prop] = ann
            else when (prop.returnType.classifier) {
                Boolean::class -> flagPropertiesToArgumentAnnotation[prop] = ann
                String::class -> singlePropertiesToArgumentAnnotation[prop] = ann
                Array<String>::class -> multiplePropertiesToArgumentAnnotation[prop] = ann
                else -> throw IllegalStateException("Unsupported argument type: ${prop.returnType}")
            }
        }
        DividedPropertiesWithArgumentAnnotationInfo(
            flagPropertiesToArgumentAnnotation,
            singlePropertiesToArgumentAnnotation,
            multiplePropertiesToArgumentAnnotation,
            classpathPropertiesToArgumentAnnotation
        )
    }
}


/**
 * Creates deep copy in order to avoid holding links to Proxy objects created by gradle tooling api
 */
fun CachedCompilerArgumentBySourceSet.deepCopy(): CachedCompilerArgumentBySourceSet =
    entries.associate { it.key to CachedArgsInfoImpl(it.value) }
