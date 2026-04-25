package com.zhoulesin.zutils.engine.core

interface FunctionRegistry {
    fun register(function: ZFunction)
    fun unregister(name: String)
    fun get(name: String): ZFunction?
    fun getAllInfos(): List<FunctionInfo>
    fun contains(name: String): Boolean
    fun size(): Int
}
