/*
 * Copyright 2010-2020 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.caching

import org.jetbrains.kotlin.cli.common.arguments.CommonToolArguments
import org.jetbrains.kotlin.gradle.getMethodOrNull
import java.lang.reflect.Proxy
import kotlin.reflect.KProperty1
import kotlin.reflect.full.declaredMemberProperties

typealias CachedCompilerArgumentBySourceSet = Map<String, CachedArgsInfo>
typealias FlatCompilerArgumentBySourceSet = Map<String, FlatArgsInfo>

const val ARGUMENT_ANNOTATION_CLASS = "org.jetbrains.kotlin.cli.common.arguments.Argument"
const val PARSE_ARGUMENTS_CLASS = "org.jetbrains.kotlin.cli.common.arguments.ParseCommandLineArgumentsKt"
const val COMMON_ARGUMENTS_CLASS = "org.jetbrains.kotlin.cli.common.arguments.CommonCompilerArguments"
const val JVM_ARGUMENTS_CLASS = "org.jetbrains.kotlin.cli.common.arguments.K2JVMCompilerArguments"
const val METADATA_ARGUMENTS_CLASS = "org.jetbrains.kotlin.cli.common.arguments.K2MetadataCompilerArguments"

data class ArgumentAnnotationInfo(
    val value: String,
    val shortName: String,
    val delimiter: String,
    val isAdvanced: Boolean
)

@Suppress("UNCHECKED_CAST")
fun obtainArgumentAnnotationInfo(classLoader: ClassLoader?, className: String, propertyName: String): ArgumentAnnotationInfo? {
    val (clazz, argumentClazz) = try {
        Class.forName(className, false, classLoader) to Class.forName(ARGUMENT_ANNOTATION_CLASS, false, classLoader)
    } catch (e: ClassNotFoundException) {
        //It can be old version mpp gradle plugin. Supported only 1.4+
        return null
    }

    val requiredProperty = clazz.kotlin.declaredMemberProperties.find { it.name == propertyName }
    val argumentAnnotation = requiredProperty?.annotations?.find {
        Proxy.isProxyClass(it::class.java)
                && (it.javaClass.getMethodOrNull("annotationType")?.invoke(it) as Class<*>).name == ARGUMENT_ANNOTATION_CLASS
                || it::class.java.kotlin == argumentClazz.kotlin
    } ?: return null
    val value = argumentAnnotation.javaClass.getMethodOrNull("value")?.invoke(argumentAnnotation) as? String ?: return null
    val shortName = argumentAnnotation.javaClass.getMethodOrNull("shortName")?.invoke(argumentAnnotation) as? String ?: return null
    val delimiter = argumentAnnotation.javaClass.getMethodOrNull("delimiter")?.invoke(argumentAnnotation) as? String ?: return null

    val isAdvanced = Class.forName(PARSE_ARGUMENTS_CLASS, false, classLoader)
        .getMethodOrNull("isAdvanced", argumentClazz)
        ?.invoke(null, argumentAnnotation) as? Boolean ?: return null

    return ArgumentAnnotationInfo(value, shortName, delimiter, isAdvanced)
}

/**
 * Creates deep copy in order to avoid holding links to Proxy objects created by gradle tooling api
 */
fun CachedCompilerArgumentBySourceSet.deepCopy(): CachedCompilerArgumentBySourceSet =
    entries.associate { it.key to CachedArgsInfoImpl(it.value) }

fun FlatCompilerArgumentsBucket.extractSingleGeneralArgumentOrNull(
    classLoader: ClassLoader, className: String, methodName: String
): String? {
    val argumentAnnotationInfo = obtainArgumentAnnotationInfo(classLoader, className, methodName) ?: return null
    return if (argumentAnnotationInfo.isAdvanced) {
        with(argumentAnnotationInfo) {
            generalArguments.firstOrNull { it.startsWith(value) }?.removePrefix("${value}=")
                ?: generalArguments.firstOrNull { it.startsWith(shortName) }?.removePrefix("${shortName}=")
        }
    } else {
        with(argumentAnnotationInfo) {
            (generalArguments.indexOf(value).takeIf { it != -1 } ?: generalArguments.indexOf(shortName).takeIf { it != -1 })
                ?.let { generalArguments.getOrNull(it + 1) }
        }
    }
}

fun FlatCompilerArgumentsBucket.setSingleGeneralArgument(
    classLoader: ClassLoader,
    className: String,
    propertyName: String,
    newValue: String?
) {
    val argumentAnnotationInfo = obtainArgumentAnnotationInfo(classLoader, className, propertyName) ?: return
    if (argumentAnnotationInfo.isAdvanced) {
        generalArguments.removeIf { it.startsWith(argumentAnnotationInfo.value) || it.startsWith(argumentAnnotationInfo.shortName) }
        newValue?.let { "${argumentAnnotationInfo.value}=$it" }?.also { generalArguments.add(it) }
    } else {
        with(argumentAnnotationInfo) {
            (generalArguments.indexOfFirst { it == value }.takeIf { it != -1 }
                ?: generalArguments.indexOfFirst { it == shortName }.takeIf { it != -1 })
                ?.let { listOf(generalArguments[it], generalArguments[it + 1]) }
                ?.also { generalArguments.removeAll(it) }
            newValue?.also {
                generalArguments.apply {
                    add(value)
                    add(it)
                }
            }
        }
    }
}

fun FlatCompilerArgumentsBucket.extractGeneralFlag(
    classLoader: ClassLoader,
    className: String,
    propertyName: String
): Boolean = obtainArgumentAnnotationInfo(classLoader, className, propertyName)?.value in generalArguments

fun <T : CommonToolArguments> FlatCompilerArgumentsBucket.setGeneralFlag(
    classLoader: ClassLoader,
    className: String,
    propertyName: String,
    newValue: Boolean
) {
    val argumentAnnotationInfo = obtainArgumentAnnotationInfo(classLoader, className, propertyName) ?: return
    if (newValue && argumentAnnotationInfo.value !in generalArguments && argumentAnnotationInfo.shortName !in generalArguments)
        generalArguments.add(propertyName)
    if (!newValue) generalArguments.removeIf { it == argumentAnnotationInfo.value || it == argumentAnnotationInfo.shortName }
}

fun FlatCompilerArgumentsBucket.extractArrayGeneralArgument(
    classLoader: ClassLoader,
    className: String,
    propertyName: String
): Array<String>? {
    val delimiter = obtainArgumentAnnotationInfo(classLoader, className, propertyName)?.delimiter ?: return null
    return extractSingleGeneralArgumentOrNull(classLoader, className, propertyName)
        ?.split(delimiter)
        ?.toTypedArray()
}


fun <T : CommonToolArguments> FlatCompilerArgumentsBucket.setArrayGeneralArgument(
    classLoader: ClassLoader,
    className: String,
    propertyName: String,
    newValue: Array<String>?
) {
    val delimiter = obtainArgumentAnnotationInfo(classLoader, className, propertyName)?.delimiter ?: return
    val joined = newValue?.joinToString(delimiter)
    setSingleGeneralArgument(classLoader, className, propertyName, joined)
}
