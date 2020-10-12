/*
 * Copyright 2010-2020 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.caching

typealias CachedCompilerArgumentBySourceSet = Map<String, CachedArgsInfo>

/**
 * Creates deep copy in order to avoid holding links to Proxy objects created by gradle tooling api
 */
fun CachedCompilerArgumentBySourceSet.deepCopy(): CachedCompilerArgumentBySourceSet {
    val result = HashMap<String, CachedArgsInfo>()
    this.forEach { key, value -> result[key] = CachedArgsInfoImpl(value) }
    return result
}

data class CompilerArgumentMappersContainer(
    val projectCompilerArgumentsMapper: CompilerArgumentsMapperWithMerge = CompilerArgumentsMapperWithMerge(),
    val projectMppCompilerArgumentsMapper: CompilerArgumentsMapperWithMerge = CompilerArgumentsMapperWithMerge()
) {
    fun mergeArgumentsFromMapper(mapper: ICompilerArgumentsMapper, isMpp: Boolean = false) {
        if (isMpp) projectMppCompilerArgumentsMapper.mergeMapper(mapper) else projectCompilerArgumentsMapper.mergeMapper(mapper)
    }
}
