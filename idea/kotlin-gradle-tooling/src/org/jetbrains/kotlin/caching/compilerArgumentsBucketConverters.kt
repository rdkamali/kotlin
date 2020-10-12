/*
 * Copyright 2010-2020 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.caching

import java.io.File

const val FRIEND_PATH_PREFIX = "-Xfriend-paths="
const val PLUGIN_CLASSPATH_PREFIX = "-Xplugin="

val classpathArgPointers = setOf("-classpath", "-cp")


interface CompilerArgumentsBucketConverter<From, To> {
    fun convert(from: From): To
}

class CachedToFlatCompilerArgumentsBucketConverter(val mapper: ICompilerArgumentsMapper) :
    CompilerArgumentsBucketConverter<CachedCompilerArgumentsBucket, FlatCompilerArgumentsBucket> {
    override fun convert(from: CachedCompilerArgumentsBucket): FlatCompilerArgumentsBucket {
        val cachedGeneralArguments = from.generalArguments.map { mapper.getArgument(it) }.toTypedArray()
        val cachedClasspath = from.classpathParts.map { mapper.getArgument(it) }.toTypedArray()
        val cachedPluginClasspath = from.pluginClasspaths.map { mapper.getArgument(it) }.toTypedArray()
        val cachedFriendPaths = from.friendPaths.map { mapper.getArgument(it) }.toTypedArray()
        return FlatCompilerArgumentsBucket(cachedGeneralArguments, cachedClasspath, cachedPluginClasspath, cachedFriendPaths)
    }
}

class FlatToRawCompilerArgumentsBucketConverter :
    CompilerArgumentsBucketConverter<FlatCompilerArgumentsBucket, RawCompilerArgumentsBucket> {
    override fun convert(from: FlatCompilerArgumentsBucket): RawCompilerArgumentsBucket = mutableListOf<String>().apply {
        addAll(from.generalArguments.toList())
        if (from.classpathParts.isNotEmpty()) {
            add(from.classpathParts.first())
            add(from.classpathParts.toMutableList().apply { removeFirst() }.joinToString(File.pathSeparator))
        }
        add("$PLUGIN_CLASSPATH_PREFIX${from.pluginClasspaths.joinToString(",")}")
        add("$FRIEND_PATH_PREFIX${from.friendPaths.joinToString(",")}")
    }
}

class CachedToRawCompilerArgumentsBucketConverter(val mapper: ICompilerArgumentsMapper) :
    CompilerArgumentsBucketConverter<CachedCompilerArgumentsBucket, RawCompilerArgumentsBucket> {
    override fun convert(from: CachedCompilerArgumentsBucket): RawCompilerArgumentsBucket =
        CachedToFlatCompilerArgumentsBucketConverter(mapper).convert(from).let {
            FlatToRawCompilerArgumentsBucketConverter().convert(it)
        }
}

class RawToFlatCompilerArgumentsBucket :
    CompilerArgumentsBucketConverter<RawCompilerArgumentsBucket, FlatCompilerArgumentsBucket> {
    override fun convert(from: RawCompilerArgumentsBucket): FlatCompilerArgumentsBucket {
        val pluginClasspathArgument = from.firstOrNull { it.startsWith(PLUGIN_CLASSPATH_PREFIX) }
        val friendPathsArgument = from.firstOrNull { it.startsWith(FRIEND_PATH_PREFIX) }
        val classpathArgument = from.flatMapIndexed { index, s ->
            if (s in classpathArgPointers) listOf(s, from[index + 1]) else emptyList()
        }

        // TODO(ychernyshev) Does the order of arguments required here?
        val generalArguments = (from - pluginClasspathArgument - friendPathsArgument - classpathArgument).filterNotNull().toTypedArray()
        val classpathArguments = if (classpathArgument.isNotEmpty())
            mutableListOf(classpathArgument.first()).apply { addAll(classpathArgument.last().split(File.pathSeparator)) }.toTypedArray()
        else emptyArray()

        val pluginClasspaths =
            pluginClasspathArgument?.removePrefix(PLUGIN_CLASSPATH_PREFIX)?.split(",")?.toTypedArray() ?: emptyArray()

        val friendPaths = friendPathsArgument?.removePrefix(FRIEND_PATH_PREFIX)?.split(",")?.toTypedArray() ?: emptyArray()

        return FlatCompilerArgumentsBucket(generalArguments, classpathArguments, pluginClasspaths, friendPaths)
    }
}

class FlatToCachedCompilerArgumentsBucket(val mapper: ICompilerArgumentsMapper) :
    CompilerArgumentsBucketConverter<FlatCompilerArgumentsBucket, CachedCompilerArgumentsBucket> {
    override fun convert(from: FlatCompilerArgumentsBucket): CachedCompilerArgumentsBucket {
        val generalArguments = from.generalArguments.map { mapper.cacheArgument(it) }.toTypedArray()
        val classpathParts = from.classpathParts.map { mapper.cacheArgument(it) }.toTypedArray()
        val pluginClasspaths = from.pluginClasspaths.map { mapper.cacheArgument(it) }.toTypedArray()
        val friendPaths = from.friendPaths.map { mapper.cacheArgument(it) }.toTypedArray()
        return CachedCompilerArgumentsBucket(generalArguments, classpathParts, pluginClasspaths, friendPaths)
    }
}

class RawToCachedCompilerArgumentsBucket(val mapper: ICompilerArgumentsMapper) :
    CompilerArgumentsBucketConverter<RawCompilerArgumentsBucket, CachedCompilerArgumentsBucket> {
    override fun convert(from: RawCompilerArgumentsBucket): CachedCompilerArgumentsBucket =
        RawToFlatCompilerArgumentsBucket().convert(from).let {
            FlatToCachedCompilerArgumentsBucket(mapper).convert(it)
        }
}