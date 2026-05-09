package com.zhoulesin.zutils.plugin.bridge

import com.zhoulesin.zutils.engine.bridge.ApiBridge
import com.zhoulesin.zutils.engine.core.*
import kotlinx.serialization.json.JsonObject

class BridgeDexAdapter(
    private val instance: Any,
    private val functionName: String,
    private val description: String = "",
) : ZFunction {

    override val info = FunctionInfo(
        name = functionName,
        description = description,
        parameters = emptyList(),
        outputType = OutputType.TEXT,
    )

    override suspend fun execute(context: ExecutionContext, args: JsonObject): ZResult {
        return try {
            val paramsMap = mutableMapOf<String, String>()
            for ((k, v) in args) {
                paramsMap[k] = when (v) {
                    is kotlinx.serialization.json.JsonPrimitive -> v.content
                    else -> v.toString()
                }
            }
            val executeMethod = instance.javaClass.getDeclaredMethod("execute", Map::class.java)
            val result = executeMethod.invoke(instance, paramsMap)
            ZResult.ok(result?.toString() ?: "执行成功")
        } catch (e: Exception) {
            ZResult.fail("桥接执行失败: ${e.message}", "BRIDGE_ERROR")
        }
    }

    companion object {
        fun create(dexClassLoader: ClassLoader, className: String, apiBridge: ApiBridge): BridgeDexAdapter {
            val clazz = dexClassLoader.loadClass(className)
            val instance = clazz.getDeclaredConstructor().newInstance()
            val setBridge = clazz.getDeclaredMethod("setApiBridge", ApiBridge::class.java)
            setBridge.invoke(instance, apiBridge)
            val name = clazz.simpleName
            return BridgeDexAdapter(instance, name)
        }
    }
}
