/*
 * Copyright 2010-2020 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.caching

import java.io.File

interface CompilerArgumentsBucketConverter<From, To> {
    val classLoader: ClassLoader?
    fun convert(from: From): To
}

class CachedToFlatCompilerArgumentsBucketConverter(val mapper: ICompilerArgumentsMapper, override val classLoader: ClassLoader? = null) :
    CompilerArgumentsBucketConverter<CachedCompilerArgumentsBucket, FlatCompilerArgumentsBucket> {
    override fun convert(from: CachedCompilerArgumentsBucket): FlatCompilerArgumentsBucket {
        val flattenSingleArguments = from.singleArguments.entries.associate { (k, v) ->
            mapper.getArgument(k) to mapper.getArgument(v)
        }
        val flattenMultipleArguments = from.multipleArguments.entries.associate { (k, v) ->
            mapper.getArgument(k) to v.map { mapper.getArgument(it) }
        }
        val flattenFlagArguments = from.flagArguments.map { mapper.getArgument(it) }
        val flattenClasspathParts = from.classpathParts?.let { (k, v) ->
            mapper.getArgument(k) to v.map { mapper.getArgument(it) }
        }
        return FlatCompilerArgumentsBucket(flattenClasspathParts).apply {
            singleArguments.putAll(flattenSingleArguments)
            multipleArguments.putAll(flattenMultipleArguments)
            flagArguments.addAll(flattenFlagArguments)
        }
    }
}

class FlatToRawCompilerArgumentsBucketConverter(override val classLoader: ClassLoader? = null) :
    CompilerArgumentsBucketConverter<FlatCompilerArgumentsBucket, RawCompilerArgumentsBucket> {
    override fun convert(from: FlatCompilerArgumentsBucket): RawCompilerArgumentsBucket = mutableListOf<String>().apply {
        val dividedPropertiesWithArgumentAnnotationInfo =
            DividedPropertiesWithArgumentAnnotationInfoManager(classLoader).dividedPropertiesWithArgumentAnnotationInfo

        val singleArgumentAnnotationInfos = dividedPropertiesWithArgumentAnnotationInfo.singlePropertiesToArgumentAnnotation.values
        from.singleArguments.entries.forEach { (k, v) ->
            val argument = singleArgumentAnnotationInfos.first { it.isSuitableValue(k) }
            if (argument.isAdvanced) {
                this@apply.add("$k=$v")
            } else {
                this@apply.add(k)
                this@apply.add(v)
            }
        }

        from.multipleArguments.entries.forEach { (k, v) ->
            val argument = singleArgumentAnnotationInfos.first { it.isSuitableValue(k) }
            val value = v.joinToString(argument.delimiter)
            if (argument.isAdvanced) {
                this@apply.add("$k=$value")
            } else {
                this@apply.add(k)
                this@apply.add(value)
            }
        }

        addAll(from.flagArguments)

        from.classpathParts?.also {
            val classpathAnnotationInfo = dividedPropertiesWithArgumentAnnotationInfo.classpathPropertiesToArgumentAnnotation.values.first()
            check(classpathAnnotationInfo.isSuitableValue(it.first)) {
                "Unexpected classpath argument \"$it\"!"
            }
            this@apply.add(it.first)
            this@apply.add(it.second.joinToString(File.pathSeparator))
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
        val dividedPropertiesWithArgumentAnnotationInfo =
            DividedPropertiesWithArgumentAnnotationInfoManager(classLoader).dividedPropertiesWithArgumentAnnotationInfo

        val singlePropertiesToArgumentAnnotation = dividedPropertiesWithArgumentAnnotationInfo.singlePropertiesToArgumentAnnotation
        val multiplePropertiesToArgumentAnnotation = dividedPropertiesWithArgumentAnnotationInfo.multiplePropertiesToArgumentAnnotation
        val flagPropertiesToArgumentAnnotation = dividedPropertiesWithArgumentAnnotationInfo.flagPropertiesToArgumentAnnotation
        val classpathPropertiesToArgumentAnnotation = dividedPropertiesWithArgumentAnnotationInfo.classpathPropertiesToArgumentAnnotation

        val classpathArgumentAnnotation = classpathPropertiesToArgumentAnnotation.values.first()
        val classpathParts = from.indexOfFirst { classpathArgumentAnnotation.isSuitableValue(it) }.takeIf { it != -1 }
            ?.let { from[it] to from[it + 1].split(File.pathSeparator) }

        fun ArgumentAnnotationInfo.processArgumentWithInfo(): Pair<String, String>? = if (isAdvanced) {
            val found = from.find { isSuitableValue(it) }
            val separate = found?.split('=')?.toTypedArray()
            check(2 == separate?.size) { "Bad \"${value}\" compiler argument value: $found " }
            separate?.let { it[0] to it[1] }
        } else from.indexOfFirst { isSuitableValue(it) }.takeIf { it != -1 }?.let { from[it] to from[it + 1] }


        val flattenSingleArguments = singlePropertiesToArgumentAnnotation.values.mapNotNull { it.processArgumentWithInfo() }.toMap()
        val flattenMultipleArguments = multiplePropertiesToArgumentAnnotation.values.mapNotNull { info ->
            info.processArgumentWithInfo()?.let { it.first to it.second.split(info.delimiter) }
        }
        val flatFlagArguments = flagPropertiesToArgumentAnnotation.values.mapNotNull { info -> from.find { info.isSuitableValue(it) } }

        return FlatCompilerArgumentsBucket(classpathParts).apply {
            singleArguments.putAll(flattenSingleArguments)
            multipleArguments.putAll(flattenMultipleArguments)
            flagArguments.addAll(flatFlagArguments)
        }
    }
}

class FlatToCachedCompilerArgumentsBucketConverter(val mapper: ICompilerArgumentsMapper, override val classLoader: ClassLoader? = null) :
    CompilerArgumentsBucketConverter<FlatCompilerArgumentsBucket, CachedCompilerArgumentsBucket> {
    override fun convert(from: FlatCompilerArgumentsBucket): CachedCompilerArgumentsBucket {
        val cachedSingleArguments = from.singleArguments.entries.associate {
            mapper.cacheArgument(it.key) to mapper.cacheArgument(it.value)
        }
        val cachedMultipleArguments = from.multipleArguments.entries.associate {
            mapper.cacheArgument(it.key) to it.value.map { v -> mapper.cacheArgument(v) }
        }
        val cachedFlagArguments = from.flagArguments.map { mapper.cacheArgument(it) }
        val cachedClasspathParts =
            from.classpathParts?.let { mapper.cacheArgument(it.first) to it.second.map { v -> mapper.cacheArgument(v) } }

        return CachedCompilerArgumentsBucket(cachedClasspathParts).apply {
            singleArguments.putAll(cachedSingleArguments)
            multipleArguments.putAll(cachedMultipleArguments)
            flagArguments.addAll(cachedFlagArguments)
        }

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
