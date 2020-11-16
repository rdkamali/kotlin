/*
 * Copyright 2010-2020 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.caching

import org.jetbrains.kotlin.cli.common.arguments.Argument
import org.jetbrains.kotlin.cli.common.arguments.CommonCompilerArguments
import org.jetbrains.kotlin.cli.common.arguments.K2JVMCompilerArguments
import org.jetbrains.kotlin.utils.addToStdlib.ifNotEmpty
import java.io.File

interface CompilerArgumentsBucketConverter<From, To> {
    fun convert(from: From): To
}

private val pluginClasspathsArgument by lazy {
    CommonCompilerArguments::pluginClasspaths.argumentAnnotation
}

private val friendPathsArgument by lazy {
    K2JVMCompilerArguments::friendPaths.argumentAnnotation
}

private val classpathArgument by lazy {
    K2JVMCompilerArguments::classpath.argumentAnnotation
}


class CachedToFlatCompilerArgumentsBucketConverter(val mapper: ICompilerArgumentsMapper) :
    CompilerArgumentsBucketConverter<CachedCompilerArgumentsBucket, FlatCompilerArgumentsBucket> {
    override fun convert(from: CachedCompilerArgumentsBucket): FlatCompilerArgumentsBucket {
        val cachedGeneralArguments = from.generalArguments.map { mapper.getArgument(it) }.toMutableList()
        val cachedClasspath = from.classpathParts.map { mapper.getArgument(it) }.toMutableList()
        val cachedPluginClasspath = from.pluginClasspaths.map { mapper.getArgument(it) }.toMutableList()
        val cachedFriendPaths = from.friendPaths.map { mapper.getArgument(it) }.toMutableList()
        return FlatCompilerArgumentsBucket(cachedGeneralArguments, cachedClasspath, cachedPluginClasspath, cachedFriendPaths)
    }
}

class FlatToRawCompilerArgumentsBucketConverter :
    CompilerArgumentsBucketConverter<FlatCompilerArgumentsBucket, RawCompilerArgumentsBucket> {
    override fun convert(from: FlatCompilerArgumentsBucket): RawCompilerArgumentsBucket = mutableListOf<String>().apply {
        addAll(from.generalArguments.toList())
        from.classpathParts.ifNotEmpty {
            this@apply.add(first())
            this@apply.add(drop(1).joinToString(File.pathSeparator))
        }
        from.pluginClasspaths.ifNotEmpty { add("${pluginClasspathsArgument.value}=${joinToString(pluginClasspathsArgument.delimiter)}") }
        from.friendPaths.ifNotEmpty { add("${friendPathsArgument.value}=${joinToString(friendPathsArgument.delimiter)}") }
    }
}

class CachedToRawCompilerArgumentsBucketConverter(val mapper: ICompilerArgumentsMapper) :
    CompilerArgumentsBucketConverter<CachedCompilerArgumentsBucket, RawCompilerArgumentsBucket> {
    private val cachedToFlatCompilerArgumentsBucketConverter by lazy { CachedToFlatCompilerArgumentsBucketConverter(mapper) }
    private val flatToRawCompilerArgumentsBucketConverter by lazy { FlatToRawCompilerArgumentsBucketConverter() }
    override fun convert(from: CachedCompilerArgumentsBucket): RawCompilerArgumentsBucket =
        cachedToFlatCompilerArgumentsBucketConverter.convert(from).let {
            flatToRawCompilerArgumentsBucketConverter.convert(it)
        }
}

class RawToFlatCompilerArgumentsBucketConverter :
    CompilerArgumentsBucketConverter<RawCompilerArgumentsBucket, FlatCompilerArgumentsBucket> {
    override fun convert(from: RawCompilerArgumentsBucket): FlatCompilerArgumentsBucket {
        val existingPluginClasspaths = from.firstOrNull { it.startsWith(pluginClasspathsArgument.value) }
        val existingFriendPaths = from.firstOrNull { it.startsWith(friendPathsArgument.value) }
        val existingClasspaths = from.flatMapIndexed { index, s ->
            if (s in setOf(classpathArgument.value, classpathArgument.shortName)) listOf(s, from[index + 1]) else emptyList()
        }

        // TODO(ychernyshev) Does the order of arguments required here?
        val generalArguments = (from - existingPluginClasspaths - existingFriendPaths - existingClasspaths).filterNotNull().toMutableList()
        val classpathArguments = existingClasspaths.ifNotEmpty {
            mutableListOf(existingClasspaths.first()).apply { addAll(existingClasspaths.last().split(File.pathSeparator)) }
        } ?: mutableListOf()

        val pluginClasspaths = existingPluginClasspaths?.removePrefix(pluginClasspathsArgument.value)
            ?.split(pluginClasspathsArgument.delimiter)
            .orEmpty()
            .toMutableList()

        val friendPaths = existingFriendPaths?.removePrefix(friendPathsArgument.value)
            ?.split(friendPathsArgument.delimiter)
            .orEmpty()
            .toMutableList()

        return FlatCompilerArgumentsBucket(generalArguments, classpathArguments, pluginClasspaths, friendPaths)
    }
}

class FlatToCachedCompilerArgumentsBucketConverter(val mapper: ICompilerArgumentsMapper) :
    CompilerArgumentsBucketConverter<FlatCompilerArgumentsBucket, CachedCompilerArgumentsBucket> {
    override fun convert(from: FlatCompilerArgumentsBucket): CachedCompilerArgumentsBucket {
        val generalArguments = from.generalArguments.map { mapper.cacheArgument(it) }.toMutableList()
        val classpathParts = from.classpathParts.map { mapper.cacheArgument(it) }.toMutableList()
        val pluginClasspaths = from.pluginClasspaths.map { mapper.cacheArgument(it) }.toMutableList()
        val friendPaths = from.friendPaths.map { mapper.cacheArgument(it) }.toMutableList()
        return CachedCompilerArgumentsBucket(generalArguments, classpathParts, pluginClasspaths, friendPaths)
    }
}

class RawToCachedCompilerArgumentsBucketConverter(val mapper: ICompilerArgumentsMapper) :
    CompilerArgumentsBucketConverter<RawCompilerArgumentsBucket, CachedCompilerArgumentsBucket> {
    private val rawToFlatCompilerArgumentsBucketConverter by lazy { RawToFlatCompilerArgumentsBucketConverter() }
    private val flatToCachedCompilerArgumentsBucketConverter by lazy { FlatToCachedCompilerArgumentsBucketConverter(mapper) }
    override fun convert(from: RawCompilerArgumentsBucket): CachedCompilerArgumentsBucket =
        rawToFlatCompilerArgumentsBucketConverter.convert(from).let {
            flatToCachedCompilerArgumentsBucketConverter.convert(it)
        }
}
