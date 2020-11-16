/*
 * Copyright 2010-2020 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.caching

import org.jetbrains.kotlin.cli.common.arguments.Argument
import org.jetbrains.kotlin.cli.common.arguments.CommonToolArguments
import org.jetbrains.kotlin.cli.common.arguments.isAdvanced
import kotlin.reflect.KProperty1

typealias CachedCompilerArgumentBySourceSet = Map<String, CachedArgsInfo>
typealias FlatCompilerArgumentBySourceSet = Map<String, FlatArgsInfo>

val KProperty1<*, *>.argumentAnnotation
    get() = annotations.first { it is Argument } as Argument

/**
 * Creates deep copy in order to avoid holding links to Proxy objects created by gradle tooling api
 */
fun CachedCompilerArgumentBySourceSet.deepCopy(): CachedCompilerArgumentBySourceSet = entries.associate { it.key to CachedArgsInfoImpl(it.value) }

fun <T : CommonToolArguments> FlatCompilerArgumentsBucket.extractSingleGeneralArgumentOrNull(
    property: KProperty1<T, *>
): String? {
    val argumentAnno = property.argumentAnnotation ?: return null
    val propertyName = argumentAnno.value
    return if (argumentAnno.isAdvanced) {
        generalArguments.firstOrNull { it.startsWith(propertyName) }?.removePrefix("${propertyName}=")
    } else {
        generalArguments.indexOf(propertyName).takeIf { it != -1 }?.let { generalArguments.getOrNull(it + 1) }
    }
}

fun <T : CommonToolArguments> FlatCompilerArgumentsBucket.setSingleGeneralArgument(
    property: KProperty1<T, *>,
    newValue: String?
) {
    val propertyArgument = property.argumentAnnotation ?: return
    val argumentName = propertyArgument.value
    if (propertyArgument.isAdvanced) {
        generalArguments.removeAll { it.startsWith(argumentName) }
        newValue?.let { "${argumentName}=$it" }?.also { generalArguments.add(it) }
    } else {
        generalArguments.indexOfFirst { it == argumentName }.takeIf { it != -1 }
            ?.let { listOf(generalArguments[it], generalArguments[it + 1]) }
            ?.also { generalArguments.removeAll(it) }
        newValue?.also {
            generalArguments.apply {
                add(argumentName)
                add(it)
            }
        }
    }
}

fun <T : CommonToolArguments> FlatCompilerArgumentsBucket.extractGeneralFlag(
    property: KProperty1<T, *>
): Boolean {
    val argumentAnnotation = property.argumentAnnotation ?: return false
    val propertyName = argumentAnnotation.value
    return generalArguments.contains(propertyName)
}

fun <T : CommonToolArguments> FlatCompilerArgumentsBucket.setGeneralFlag(
    property: KProperty1<T, *>,
    newValue: Boolean
) {
    val argumentAnno = property.argumentAnnotation ?: return
    val propertyName = argumentAnno.value
    if (newValue && !generalArguments.contains(propertyName)) generalArguments.add(propertyName)
    if (!newValue) generalArguments.remove(propertyName)
}

fun <T : CommonToolArguments> FlatCompilerArgumentsBucket.extractArrayGeneralArgument(
    property: KProperty1<T, *>
): Array<String>? {
    val delimeter = property.argumentAnnotation?.delimiter ?: return null
    return extractSingleGeneralArgumentOrNull(property)
        ?.split(delimeter)
        ?.toTypedArray()
}


fun <T : CommonToolArguments> FlatCompilerArgumentsBucket.setArrayGeneralArgument(
    property: KProperty1<T, *>,
    newValue: Array<String>?
) {
    val delimeter = property.argumentAnnotation?.delimiter ?: return
    val joined = newValue?.joinToString(delimeter)
    setSingleGeneralArgument(property, joined)
}

