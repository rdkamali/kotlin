/*
 * Copyright 2010-2017 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.jetbrains.kotlin.gradle.internal

import com.intellij.util.PathUtil
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.Internal
import org.jetbrains.kotlin.cli.common.arguments.*
import org.jetbrains.kotlin.compilerRunner.ArgumentUtils
import java.io.File

interface CompilerArgumentAware<T : CommonToolArguments> {
    val serializedCompilerArguments: List<String>
        get() = ArgumentUtils.convertArgumentsToStringList(prepareCompilerArguments())

    val serializedCompilerArgumentsIgnoreClasspathIssues: List<String>
        get() = ArgumentUtils.convertArgumentsToStringList(prepareCompilerArguments(ignoreClasspathResolutionErrors = true))

    val defaultSerializedCompilerArguments: List<String>
        get() = createCompilerArgs()
            .also { setupCompilerArgs(it, defaultsOnly = true) }
            .let(ArgumentUtils::convertArgumentsToStringList)

    val filteredArgumentsMap: Map<String, String>
        get() = CompilerArgumentsGradleInput.createInputsMap(prepareCompilerArguments())

    val serializedCompilerArgumentsForBucket: Array<Array<String>>
        get() = compilerArgumentsSplitter().splitCompilerArguments(prepareCompilerArguments(ignoreClasspathResolutionErrors = true))

    val defaultSerializedCompilerArgumentsForBucket: Array<Array<String>>
        get() = createCompilerArgs().also { setupCompilerArgs(it, defaultsOnly = true) }.let {
            compilerArgumentsSplitter().splitCompilerArguments(it)
        }

    fun createCompilerArgs(): T
    fun setupCompilerArgs(args: T, defaultsOnly: Boolean = false, ignoreClasspathResolutionErrors: Boolean = false)
    fun compilerArgumentsSplitter(): CompilerArgumentsSplitter<T>
}

internal fun <T : CommonToolArguments> CompilerArgumentAware<T>.prepareCompilerArguments(ignoreClasspathResolutionErrors: Boolean = false) =
    createCompilerArgs().also { setupCompilerArgs(it, ignoreClasspathResolutionErrors = ignoreClasspathResolutionErrors) }

interface CompilerArgumentAwareWithInput<T : CommonToolArguments> : CompilerArgumentAware<T> {
    @get:Internal
    override val serializedCompilerArguments: List<String>
        get() = super.serializedCompilerArguments

    @get:Internal
    override val defaultSerializedCompilerArguments: List<String>
        get() = super.defaultSerializedCompilerArguments

    @get:Internal
    override val serializedCompilerArgumentsIgnoreClasspathIssues: List<String>
        get() = super.serializedCompilerArgumentsIgnoreClasspathIssues

    @get:Input
    override val filteredArgumentsMap: Map<String, String>
        get() = super.filteredArgumentsMap

    @get:Internal
    override val serializedCompilerArgumentsForBucket: Array<Array<String>>
        get() = super.serializedCompilerArgumentsForBucket

    @get:Internal
    override val defaultSerializedCompilerArgumentsForBucket: Array<Array<String>>
        get() = super.defaultSerializedCompilerArgumentsForBucket
}

interface CompilerArgumentsSplitter<T : CommonToolArguments> {
    fun splitCompilerArguments(args: T): Array<Array<String>>
}

class K2JVMCompilerArgumentsSplitter : CompilerArgumentsSplitter<K2JVMCompilerArguments> {
    override fun splitCompilerArguments(args: K2JVMCompilerArguments): Array<Array<String>> {
        val classpathParts =
            args.classpath?.split(File.pathSeparator)?.map { PathUtil.toSystemIndependentName(it) }.orEmpty().toTypedArray()
        val pluginClasspaths = args.pluginClasspaths?.map { PathUtil.toSystemIndependentName(it) }.orEmpty().toTypedArray()
        val friendPaths = args.friendPaths?.map { PathUtil.toSystemIndependentName(it) }.orEmpty().toTypedArray()
        return copyBean(args).also {
            it.classpath = null
            it.pluginClasspaths = null
            it.friendPaths = null
        }.let { arrayOf(ArgumentUtils.convertArgumentsToStringList(it).toTypedArray(), classpathParts, pluginClasspaths, friendPaths) }
    }
}

class K2JSCompilerArgumentsSplitter : CompilerArgumentsSplitter<K2JSCompilerArguments> {
    override fun splitCompilerArguments(args: K2JSCompilerArguments): Array<Array<String>> {
        val pluginClasspaths = args.pluginClasspaths?.map { PathUtil.toSystemIndependentName(it) }.orEmpty().toTypedArray()
        return copyBean(args).also {
            it.pluginClasspaths = null
        }.let { arrayOf(ArgumentUtils.convertArgumentsToStringList(it).toTypedArray(), emptyArray(), pluginClasspaths, emptyArray()) }
    }
}

class K2MetadataCompilerArgumentsSplitter : CompilerArgumentsSplitter<K2MetadataCompilerArguments> {
    override fun splitCompilerArguments(args: K2MetadataCompilerArguments): Array<Array<String>> {
        val classpathParts =
            args.classpath?.split(File.pathSeparator)?.map { PathUtil.toSystemIndependentName(it) }.orEmpty().toTypedArray()
        val pluginClasspaths = args.pluginClasspaths?.map { PathUtil.toSystemIndependentName(it) }.orEmpty().toTypedArray()
        val friendPaths = args.friendPaths?.map { PathUtil.toSystemIndependentName(it) }.orEmpty().toTypedArray()
        return copyBean(args).also {
            it.classpath = null
            it.pluginClasspaths = null
            it.friendPaths = null
        }.let { arrayOf(ArgumentUtils.convertArgumentsToStringList(it).toTypedArray(), classpathParts, pluginClasspaths, friendPaths) }
    }
}

class K2JSDceArgumentsSplitter : CompilerArgumentsSplitter<K2JSDceArguments> {
    override fun splitCompilerArguments(args: K2JSDceArguments): Array<Array<String>> =
        arrayOf(ArgumentUtils.convertArgumentsToStringList(args).toTypedArray(), emptyArray(), emptyArray(), emptyArray())
}