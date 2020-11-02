/*
 * Copyright 2010-2020 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.caching

import java.io.Serializable

typealias CachedArgumentIdType = Int
typealias ClasspathArgumentCacheIdType = List<Int>
typealias ClasspathArgumentsType = List<String>
typealias RawCompilerArgumentsBucket = List<String>

interface CompilerArgumentsBucket<T> : Serializable {
    val generalArguments: T
    val classpathParts: T
    val pluginClasspaths: T
    val friendPaths: T
}

class CachedCompilerArgumentsBucket(
    override val generalArguments: List<Int>,
    override val classpathParts: List<Int>,
    override val pluginClasspaths: List<Int>,
    override val friendPaths: List<Int>
) : CompilerArgumentsBucket<List<Int>> {
    constructor(otherBucket: CachedCompilerArgumentsBucket) : this(
        otherBucket.generalArguments.toList(),
        otherBucket.classpathParts.toList(),
        otherBucket.pluginClasspaths.toList(),
        otherBucket.friendPaths.toList()
    )
}

class FlatCompilerArgumentsBucket(
    override val generalArguments: List<String>,
    override val classpathParts: List<String>,
    override val pluginClasspaths: List<String>,
    override val friendPaths: List<String>
) : CompilerArgumentsBucket<List<String>> {
    constructor(otherBucket: FlatCompilerArgumentsBucket) : this(
        otherBucket.generalArguments.toList(),
        otherBucket.classpathParts.toList(),
        otherBucket.pluginClasspaths.toList(),
        otherBucket.friendPaths.toList()
    )
}
