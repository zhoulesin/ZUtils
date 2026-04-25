package com.zhoulesin.zutils.engine.registry

import com.zhoulesin.zutils.engine.core.FunctionInfo
import com.zhoulesin.zutils.engine.core.FunctionRegistry
import com.zhoulesin.zutils.engine.core.ZFunction

class DefaultFunctionRegistry : FunctionRegistry {
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
