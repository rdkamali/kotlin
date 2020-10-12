/*
 * Copyright 2010-2020 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.caching

import java.io.Serializable

typealias CachedArgumentIdType = Int
typealias ClasspathArgumentCacheIdType = Array<Int>
typealias ClasspathArgumentsType = Array<String>
typealias RawCompilerArgumentsBucket = List<String>

interface CompilerArgumentsBucket<T> : Serializable {
    val generalArguments: T
    val classpathParts: T
    val pluginClasspaths: T
    val friendPaths: T
}

class CachedCompilerArgumentsBucket(
    override val generalArguments: Array<Int>,
    override val classpathParts: Array<Int>,
    override val pluginClasspaths: Array<Int>,
    override val friendPaths: Array<Int>
) : CompilerArgumentsBucket<Array<Int>> {
    constructor(otherBucket: CachedCompilerArgumentsBucket) : this(
        arrayOf(*otherBucket.generalArguments),
        arrayOf(*otherBucket.classpathParts),
        arrayOf(*otherBucket.pluginClasspaths),
        arrayOf(*otherBucket.friendPaths)
    )
}

class FlatCompilerArgumentsBucket(
    override val generalArguments: Array<String>,
    override val classpathParts: Array<String>,
    override val pluginClasspaths: Array<String>,
    override val friendPaths: Array<String>
) : CompilerArgumentsBucket<Array<String>> {
    constructor(otherBucket: FlatCompilerArgumentsBucket) : this(
        arrayOf(*otherBucket.generalArguments),
        arrayOf(*otherBucket.classpathParts),
        arrayOf(*otherBucket.pluginClasspaths),
        arrayOf(*otherBucket.friendPaths)
    )
}
