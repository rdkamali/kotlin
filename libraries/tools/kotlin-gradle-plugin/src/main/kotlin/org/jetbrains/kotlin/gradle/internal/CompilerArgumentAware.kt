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

import org.gradle.api.tasks.Input
import org.gradle.api.tasks.Internal
import org.jetbrains.kotlin.cli.common.arguments.*
import org.jetbrains.kotlin.compilerRunner.ArgumentUtils
import java.io.File

@Suppress("UNCHECKED_CAST")
private fun <T : CommonToolArguments> divideCompilerArguments(compArgs: T): List<List<String>> = when (compArgs) {
    is K2JVMCompilerArguments -> {
        val classpathParts = compArgs.classpath?.split(File.pathSeparator)?.map { it.replace('\\', '/') }.orEmpty()
            .also { compArgs.classpath = null }
        val pluginClasspaths = compArgs.pluginClasspaths?.map { it.replace('\\', '/') }.orEmpty()
            .also { compArgs.pluginClasspaths = null }
        val friendPaths = compArgs.friendPaths?.map { it.replace('\\', '/') }.orEmpty()
            .also { compArgs.friendPaths = null }
        listOf(ArgumentUtils.convertArgumentsToStringList(compArgs), classpathParts, pluginClasspaths, friendPaths)
    }
    is K2MetadataCompilerArguments -> {
        val classpathParts = compArgs.classpath?.split(File.pathSeparator)?.map { it.replace('\\', '/') }.orEmpty()
            .also { compArgs.classpath = null }
        val pluginClasspaths = compArgs.pluginClasspaths?.map { it.replace('\\', '/') }.orEmpty()
            .also { compArgs.pluginClasspaths = null }
        val friendPaths = compArgs.friendPaths?.map { it.replace('\\', '/') }.orEmpty()
            .also { compArgs.friendPaths = null }
        listOf(ArgumentUtils.convertArgumentsToStringList(compArgs), classpathParts, pluginClasspaths, friendPaths)
    }
    is CommonCompilerArguments -> {
        val pluginClasspaths = compArgs.pluginClasspaths?.map { it.replace('\\', '/') }.orEmpty()
            .also { compArgs.pluginClasspaths = null }
        listOf(ArgumentUtils.convertArgumentsToStringList(compArgs), emptyList(), pluginClasspaths, emptyList())
    }
    else -> {
        listOf(ArgumentUtils.convertArgumentsToStringList(compArgs), emptyList(), emptyList(), emptyList())
    }
}

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

    val serializedCompilerArgumentsForBucket: List<List<String>>
        get() = divideCompilerArguments(prepareCompilerArguments())

    val defaultSerializedCompilerArgumentsForBucket: List<List<String>>
        get() = divideCompilerArguments(createCompilerArgs().also { setupCompilerArgs(it, defaultsOnly = true) })

    fun createCompilerArgs(): T
    fun setupCompilerArgs(args: T, defaultsOnly: Boolean = false, ignoreClasspathResolutionErrors: Boolean = false)
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
    override val serializedCompilerArgumentsForBucket: List<List<String>>
        get() = super.serializedCompilerArgumentsForBucket

    @get:Internal
    override val defaultSerializedCompilerArgumentsForBucket: List<List<String>>
        get() = super.defaultSerializedCompilerArgumentsForBucket
}