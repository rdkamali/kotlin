/*
 * Copyright 2010-2020 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.caching

import java.io.Serializable

typealias ClasspathArgumentCacheIdType = List<Int>
typealias ClasspathArgumentsType = List<String>
typealias RawCompilerArgumentsBucket = List<String>

interface CompilerArgumentsBucket<T> : Serializable {
    val generalArguments: MutableList<T>
    val classpathParts: MutableList<T>
    val pluginClasspaths: MutableList<T>
    val friendPaths: MutableList<T>
}

class CachedCompilerArgumentsBucket(
    override val generalArguments: MutableList<Int> = mutableListOf(),
    override val classpathParts: MutableList<Int> = mutableListOf(),
    override val pluginClasspaths: MutableList<Int> = mutableListOf(),
    override val friendPaths: MutableList<Int> = mutableListOf()
) : CompilerArgumentsBucket<Int> {
    constructor(otherBucket: CachedCompilerArgumentsBucket) : this() {
        generalArguments.addAll(otherBucket.generalArguments)
        classpathParts.addAll(otherBucket.classpathParts)
        pluginClasspaths.addAll(otherBucket.pluginClasspaths)
        friendPaths.addAll(otherBucket.friendPaths)
    }
}

class FlatCompilerArgumentsBucket(
    override val generalArguments: MutableList<String> = mutableListOf(),
    override val classpathParts: MutableList<String> = mutableListOf(),
    override val pluginClasspaths: MutableList<String> = mutableListOf(),
    override val friendPaths: MutableList<String> = mutableListOf()
) : CompilerArgumentsBucket<String> {
    constructor(otherBucket: FlatCompilerArgumentsBucket) : this() {
        generalArguments.addAll(otherBucket.generalArguments)
        classpathParts.addAll(otherBucket.classpathParts)
        pluginClasspaths.addAll(otherBucket.pluginClasspaths)
        friendPaths.addAll(otherBucket.friendPaths)
    }
}
