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
    val classpathParts: Pair<T, List<T>>?
    val singleArguments: HashMap<T, T>
    val multipleArguments: HashMap<T, List<T>>
    val flagArguments: ArrayList<T>
}

class CachedCompilerArgumentsBucket(
    override val classpathParts: Pair<Int, List<Int>>? = null,
    override val singleArguments: HashMap<Int, Int> = hashMapOf(),
    override val multipleArguments: HashMap<Int, List<Int>> = hashMapOf(),
    override val flagArguments: ArrayList<Int> = arrayListOf()
) : CompilerArgumentsBucket<Int> {
    constructor(otherBucket: CachedCompilerArgumentsBucket) : this(otherBucket.classpathParts) {
        singleArguments.putAll(otherBucket.singleArguments)
        multipleArguments.putAll(otherBucket.multipleArguments)
        flagArguments.addAll(otherBucket.flagArguments)
    }
}

class FlatCompilerArgumentsBucket(
    override val classpathParts: Pair<String, List<String>>? = null,
    override val singleArguments: HashMap<String, String> = hashMapOf(),
    override val multipleArguments: HashMap<String, List<String>> = hashMapOf(),
    override val flagArguments: ArrayList<String> = arrayListOf()
) : CompilerArgumentsBucket<String> {
    constructor(otherBucket: FlatCompilerArgumentsBucket) : this(otherBucket.classpathParts) {
        singleArguments.putAll(otherBucket.singleArguments)
        multipleArguments.putAll(otherBucket.multipleArguments)
        flagArguments.addAll(otherBucket.flagArguments)
    }
}
