/*
 * Copyright 2010-2020 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.gradle.org.jetbrains.kotlin.caching

import org.jetbrains.kotlin.cli.common.arguments.*
import java.io.File
import java.lang.reflect.Modifier

interface CompilerArgumentsSplitter<T : CommonToolArguments> {
    fun splitCompilerArguments(args: T): Array<Array<String>>
}

@Suppress("UNCHECKED_CAST")
private inline fun <reified T : CommonToolArguments> T.toStringList(): List<String> {
    val argumentUtilsClass = Class.forName("org.jetbrains.kotlin.compilerRunner.ArgumentUtils")
    val convertMethod =
        argumentUtilsClass.declaredMethods.single { it.name == "convertArgumentsToStringList" && Modifier.isPublic(it.modifiers) }
    require(Modifier.isStatic(convertMethod.modifiers))
    return convertMethod.invoke(null, this) as? List<String> ?: emptyList()
}

class K2JVMCompilerArgumentsSplitter : CompilerArgumentsSplitter<K2JVMCompilerArguments> {
    override fun splitCompilerArguments(args: K2JVMCompilerArguments): Array<Array<String>> {
        val classpathParts =
            args.classpath?.split(File.pathSeparator)?.map { it.replace('\\', '/') }.orEmpty().toTypedArray()
        val pluginClasspaths = args.pluginClasspaths?.map { it.replace('\\', '/') }.orEmpty().toTypedArray()
        val friendPaths = args.friendPaths?.map { it.replace('\\', '/') }.orEmpty().toTypedArray()
        return copyBean(args).also {
            it.classpath = null
            it.pluginClasspaths = null
            it.friendPaths = null
        }.let { arrayOf(it.toStringList().toTypedArray(), classpathParts, pluginClasspaths, friendPaths) }
    }
}

class K2JSCompilerArgumentsSplitter : CompilerArgumentsSplitter<K2JSCompilerArguments> {
    override fun splitCompilerArguments(args: K2JSCompilerArguments): Array<Array<String>> {
        val pluginClasspaths = args.pluginClasspaths?.map { it.replace('\\', '/') }.orEmpty().toTypedArray()
        return copyBean(args).also {
            it.pluginClasspaths = null
        }.let { arrayOf(it.toStringList().toTypedArray(), emptyArray(), pluginClasspaths, emptyArray()) }
    }
}

class K2MetadataCompilerArgumentsSplitter : CompilerArgumentsSplitter<K2MetadataCompilerArguments> {
    override fun splitCompilerArguments(args: K2MetadataCompilerArguments): Array<Array<String>> {
        val classpathParts =
            args.classpath?.split(File.pathSeparator)?.map { it.replace('\\', '/') }.orEmpty().toTypedArray()
        val pluginClasspaths = args.pluginClasspaths?.map { it.replace('\\', '/') }.orEmpty().toTypedArray()
        val friendPaths = args.friendPaths?.map { it.replace('\\', '/') }.orEmpty().toTypedArray()
        return copyBean(args).also {
            it.classpath = null
            it.pluginClasspaths = null
            it.friendPaths = null
        }.let { arrayOf(it.toStringList().toTypedArray(), classpathParts, pluginClasspaths, friendPaths) }
    }
}

class K2JSDceArgumentsSplitter : CompilerArgumentsSplitter<K2JSDceArguments> {
    override fun splitCompilerArguments(args: K2JSDceArguments): Array<Array<String>> =
        arrayOf(args.toStringList().toTypedArray(), emptyArray(), emptyArray(), emptyArray())
}