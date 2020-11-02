/*
 * Copyright 2010-2020 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.caching

import java.io.Serializable

interface IArgsInfo<R, T : CompilerArgumentsBucket<R>> : Serializable {
    val currentCompilerArgumentsBucket: T
    val defaultCompilerArgumentsBucket: T
    val dependencyClasspath: R
}

interface FlatArgsInfo : IArgsInfo<ClasspathArgumentsType, FlatCompilerArgumentsBucket> {
    override val currentCompilerArgumentsBucket: FlatCompilerArgumentsBucket
    override val defaultCompilerArgumentsBucket: FlatCompilerArgumentsBucket
    override val dependencyClasspath: ClasspathArgumentsType
}

class FlatArgsInfoImpl(
    override val currentCompilerArgumentsBucket: FlatCompilerArgumentsBucket,
    override val defaultCompilerArgumentsBucket: FlatCompilerArgumentsBucket,
    override val dependencyClasspath: ClasspathArgumentsType
) : FlatArgsInfo {
    constructor(flatArgsInfo: FlatArgsInfo) : this(
        FlatCompilerArgumentsBucket(flatArgsInfo.currentCompilerArgumentsBucket),
        FlatCompilerArgumentsBucket(flatArgsInfo.defaultCompilerArgumentsBucket),
        flatArgsInfo.dependencyClasspath.toList()
    )
}

interface CachedArgsInfo : IArgsInfo<ClasspathArgumentCacheIdType, CachedCompilerArgumentsBucket> {
    override val currentCompilerArgumentsBucket: CachedCompilerArgumentsBucket
    override val defaultCompilerArgumentsBucket: CachedCompilerArgumentsBucket
    override val dependencyClasspath: ClasspathArgumentCacheIdType
}

class CachedArgsInfoImpl(
    override val currentCompilerArgumentsBucket: CachedCompilerArgumentsBucket,
    override val defaultCompilerArgumentsBucket: CachedCompilerArgumentsBucket,
    override val dependencyClasspath: ClasspathArgumentCacheIdType
) : CachedArgsInfo {
    constructor(cachedArgsInfo: CachedArgsInfo) : this(
        CachedCompilerArgumentsBucket(cachedArgsInfo.currentCompilerArgumentsBucket),
        CachedCompilerArgumentsBucket(cachedArgsInfo.defaultCompilerArgumentsBucket),
        cachedArgsInfo.dependencyClasspath.toList()
    )
}

