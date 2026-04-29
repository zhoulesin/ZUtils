package com.zhoulesin.zutils.engine.registry

import com.zhoulesin.zutils.engine.core.FunctionInfo
import com.zhoulesin.zutils.engine.core.FunctionRegistry
import com.zhoulesin.zutils.engine.core.ZFunction

/**
 * FunctionRegistry 的默认实现，基于 LinkedHashMap 保证注册顺序。
 *
 * 内置函数在 MainActivity 中注册，DEX 插件在 Engine.resolveMissingFunctions() 中注册。
 */
class DefaultFunctionRegistry : FunctionRegistry {
    /** 按注册顺序存储函数。Key 为 ZFunction.info.name。 */
    private val functions = LinkedHashMap<String, ZFunction>()

    override fun register(function: ZFunction) {
        functions[function.info.name] = function
    }

    override fun unregister(name: String) {
        functions.remove(name)
    }

    override fun get(name: String): ZFunction? = functions[name]

    override fun getAllInfos(): List<FunctionInfo> =
        functions.values.map { it.info }

    override fun contains(name: String): Boolean =
        functions.containsKey(name)

    override fun size(): Int = functions.size
}
