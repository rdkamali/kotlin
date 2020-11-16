/*
 * Copyright 2010-2020 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.caching

import org.jetbrains.kotlin.cli.common.arguments.Argument
import org.jetbrains.kotlin.cli.common.arguments.CommonCompilerArguments
import org.jetbrains.kotlin.cli.common.arguments.K2JVMCompilerArguments
import org.jetbrains.kotlin.utils.addIfNotNull
import org.jetbrains.kotlin.utils.addToStdlib.ifNotEmpty
import java.io.File

interface CompilerArgumentsBucketConverter<From, To> {
    val classLoader: ClassLoader?
    fun convert(from: From): To
}

class CachedToFlatCompilerArgumentsBucketConverter(val mapper: ICompilerArgumentsMapper, override val classLoader: ClassLoader? = null) :
    CompilerArgumentsBucketConverter<CachedCompilerArgumentsBucket, FlatCompilerArgumentsBucket> {
    override fun convert(from: CachedCompilerArgumentsBucket): FlatCompilerArgumentsBucket {
        val cachedGeneralArguments = from.generalArguments.map { mapper.getArgument(it) }.toMutableList()
        val cachedClasspath = from.classpathParts.map { mapper.getArgument(it) }.toMutableList()
        val cachedPluginClasspath = from.pluginClasspaths.map { mapper.getArgument(it) }.toMutableList()
        val cachedFriendPaths = from.friendPaths.map { mapper.getArgument(it) }.toMutableList()
        return FlatCompilerArgumentsBucket(cachedGeneralArguments, cachedClasspath, cachedPluginClasspath, cachedFriendPaths)
    }
}

class FlatToRawCompilerArgumentsBucketConverter(override val classLoader: ClassLoader? = null) :
    CompilerArgumentsBucketConverter<FlatCompilerArgumentsBucket, RawCompilerArgumentsBucket> {
    override fun convert(from: FlatCompilerArgumentsBucket): RawCompilerArgumentsBucket = mutableListOf<String>().apply {
        addAll(from.generalArguments.toList())
        from.classpathParts.ifNotEmpty {
            this@apply.add(first())
            this@apply.add(drop(1).joinToString(File.pathSeparator))
        }
        obtainArgumentAnnotationInfo(classLoader, COMMON_ARGUMENTS_CLASS, "pluginClasspaths")?.also {
            from.pluginClasspaths.ifNotEmpty { add("${it.value}=${joinToString(it.delimiter)}") }
        }
        (obtainArgumentAnnotationInfo(classLoader, JVM_ARGUMENTS_CLASS, "friendPaths")
            ?: obtainArgumentAnnotationInfo(classLoader, METADATA_ARGUMENTS_CLASS, "friendPaths"))?.also {
            from.friendPaths.ifNotEmpty { add("${it.value}=${joinToString(it.delimiter)}") }
        }
    }
}

class CachedToRawCompilerArgumentsBucketConverter(val mapper: ICompilerArgumentsMapper, override val classLoader: ClassLoader? = null) :
    CompilerArgumentsBucketConverter<CachedCompilerArgumentsBucket, RawCompilerArgumentsBucket> {
    private val cachedToFlatCompilerArgumentsBucketConverter by lazy { CachedToFlatCompilerArgumentsBucketConverter(mapper, classLoader) }
    private val flatToRawCompilerArgumentsBucketConverter by lazy { FlatToRawCompilerArgumentsBucketConverter(classLoader) }
    override fun convert(from: CachedCompilerArgumentsBucket): RawCompilerArgumentsBucket =
        cachedToFlatCompilerArgumentsBucketConverter.convert(from).let {
            flatToRawCompilerArgumentsBucketConverter.convert(it)
        }
}

class RawToFlatCompilerArgumentsBucketConverter(override val classLoader: ClassLoader? = null) :
    CompilerArgumentsBucketConverter<RawCompilerArgumentsBucket, FlatCompilerArgumentsBucket> {
    override fun convert(from: RawCompilerArgumentsBucket): FlatCompilerArgumentsBucket {
        val pluginClasspathsAnnotationInfo = obtainArgumentAnnotationInfo(classLoader, COMMON_ARGUMENTS_CLASS, "pluginClasspaths")
        val existingPluginClasspaths = pluginClasspathsAnnotationInfo?.let {
            from.firstOrNull { it1 -> it1.startsWith(it.value) }
        }

        val friendPathsAnnotationInfo = (obtainArgumentAnnotationInfo(classLoader, JVM_ARGUMENTS_CLASS, "friendPaths")
            ?: obtainArgumentAnnotationInfo(classLoader, METADATA_ARGUMENTS_CLASS, "friendPaths"))
        val existingFriendPaths = friendPathsAnnotationInfo?.let {
            from.firstOrNull { it1 -> it1.startsWith(it.value) }
        }

        val existingClasspaths = (obtainArgumentAnnotationInfo(classLoader, JVM_ARGUMENTS_CLASS, "classpath")
            ?: obtainArgumentAnnotationInfo(classLoader, METADATA_ARGUMENTS_CLASS, "classpath"))?.let {
            from.flatMapIndexed { index, s ->
                if (s in setOf(it.value, it.shortName)) listOf(s, from[index + 1]) else emptyList()
            }
        }

        // TODO(ychernyshev) Does the order of arguments required here?
        val generalArguments = from.toMutableList().apply {
            existingClasspaths?.also { removeAll(it) }
            existingPluginClasspaths?.also { remove(it) }
            existingFriendPaths?.also { remove(it) }
        }
        val classpathArguments = existingClasspaths?.ifNotEmpty {
            mutableListOf(existingClasspaths.first()).apply { addAll(existingClasspaths.last().split(File.pathSeparator)) }
        } ?: mutableListOf()

        val pluginClasspaths = pluginClasspathsAnnotationInfo?.let { existingPluginClasspaths?.removePrefix(it.value) }
            ?.split(pluginClasspathsAnnotationInfo.delimiter)
            .orEmpty()
            .toMutableList()

        val friendPaths = friendPathsAnnotationInfo?.let { existingFriendPaths?.removePrefix(it.value) }
            ?.split(friendPathsAnnotationInfo.delimiter)
            .orEmpty()
            .toMutableList()

        return FlatCompilerArgumentsBucket(generalArguments, classpathArguments, pluginClasspaths, friendPaths)
    }
}

class FlatToCachedCompilerArgumentsBucketConverter(val mapper: ICompilerArgumentsMapper, override val classLoader: ClassLoader? = null) :
    CompilerArgumentsBucketConverter<FlatCompilerArgumentsBucket, CachedCompilerArgumentsBucket> {
    override fun convert(from: FlatCompilerArgumentsBucket): CachedCompilerArgumentsBucket {
        val generalArguments = from.generalArguments.map { mapper.cacheArgument(it) }.toMutableList()
        val classpathParts = from.classpathParts.map { mapper.cacheArgument(it) }.toMutableList()
        val pluginClasspaths = from.pluginClasspaths.map { mapper.cacheArgument(it) }.toMutableList()
        val friendPaths = from.friendPaths.map { mapper.cacheArgument(it) }.toMutableList()
        return CachedCompilerArgumentsBucket(generalArguments, classpathParts, pluginClasspaths, friendPaths)
    }
}

class RawToCachedCompilerArgumentsBucketConverter(val mapper: ICompilerArgumentsMapper, override val classLoader: ClassLoader? = null) :
    CompilerArgumentsBucketConverter<RawCompilerArgumentsBucket, CachedCompilerArgumentsBucket> {
    private val rawToFlatCompilerArgumentsBucketConverter by lazy { RawToFlatCompilerArgumentsBucketConverter(classLoader) }
    private val flatToCachedCompilerArgumentsBucketConverter by lazy { FlatToCachedCompilerArgumentsBucketConverter(mapper, classLoader) }
    override fun convert(from: RawCompilerArgumentsBucket): CachedCompilerArgumentsBucket =
        rawToFlatCompilerArgumentsBucketConverter.convert(from).let {
            flatToCachedCompilerArgumentsBucketConverter.convert(it)
        }
}
