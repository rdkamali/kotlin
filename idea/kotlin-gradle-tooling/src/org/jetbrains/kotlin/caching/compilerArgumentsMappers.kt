/*
 * Copyright 2010-2020 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.caching

import java.io.Serializable

interface ICompilerArgumentsMapper : Serializable {
    fun getArgumentCache(argument: String): Int?
    fun cacheArgument(commonArgument: String): Int
    fun getArgument(id: Int): String
    fun clear()
    fun copyCache(): Map<Int, String>
}

open class CompilerArgumentsMapper(val initialId: Int = 0) : ICompilerArgumentsMapper {

    constructor(otherMapper: CompilerArgumentsMapper) : this(otherMapper.initialId) {
        idToCompilerArguments.putAll(otherMapper.idToCompilerArguments)
        nextId = otherMapper.nextId
    }

    var nextId = initialId
    protected val idToCompilerArguments: MutableMap<Int, String> = mutableMapOf()

    override fun getArgumentCache(argument: String): Int? =
        idToCompilerArguments.filter { it.value == argument }.keys.firstOrNull()

    override fun cacheArgument(commonArgument: String): Int = getArgumentCache(commonArgument)
        ?: run {
            val index = nextId++
            idToCompilerArguments[index] = commonArgument
            index
        }

    override fun getArgument(id: Int): String = idToCompilerArguments[id]!!

    override fun clear() {
        idToCompilerArguments.clear()
        nextId = initialId
    }

    override fun copyCache(): Map<Int, String> = HashMap(idToCompilerArguments)
}

interface IDetachableMapper : ICompilerArgumentsMapper {
    val masterMapper: ICompilerArgumentsMapper
    fun detach(): ICompilerArgumentsMapper
}

class DetachableCompilerArgumentsMapper(override val masterMapper: CompilerArgumentsMapper) : CompilerArgumentsMapper(),
    IDetachableMapper {
    override fun cacheArgument(commonArgument: String): Int =
        masterMapper.getArgumentCache(commonArgument) ?: masterMapper.cacheArgument(commonArgument).also {
            idToCompilerArguments[it] = commonArgument
            nextId = masterMapper.nextId
        }

    override fun detach(): ICompilerArgumentsMapper = CompilerArgumentsMapper(this)
}

interface IWithCheckoutMapper : ICompilerArgumentsMapper {
    fun checkoutMapper(): IDetachableMapper
}

class CompilerArgumentsMapperWithCheckout(initialId: Int = 0) : CompilerArgumentsMapper(initialId), IWithCheckoutMapper {
    override fun checkoutMapper(): IDetachableMapper = DetachableCompilerArgumentsMapper(this)
}

interface IWithMergeMapper : ICompilerArgumentsMapper {
    fun mergeMapper(mapper: ICompilerArgumentsMapper)
}

class CompilerArgumentsMapperWithMerge : CompilerArgumentsMapper(), IWithMergeMapper {

    override fun mergeMapper(mapper: ICompilerArgumentsMapper) {
        mapper.copyCache().forEach { (id, arg) ->
            idToCompilerArguments[id] = arg
        }
    }
}

