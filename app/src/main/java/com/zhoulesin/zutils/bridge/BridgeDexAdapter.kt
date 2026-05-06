package com.zhoulesin.zutils.bridge

import com.zhoulesin.zutils.engine.core.*
import kotlinx.serialization.json.JsonObject

/**
 * 将桥接模式 DEX 包装为 ZFunction，注册到 Engine。
 *
 * DEX 类需实现两个反射方法：
 *   setApiBridge(ApiBridge)  — 宿主注入桥
 *   execute(Map<String, String>) — LLM 传入的参数，返回结果文本
 */
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
            val result = executeMethod.invoke(instance, paramsMap) as? String ?: "执行成功"
            ZResult.ok(result)
        } catch (e: Exception) {
            ZResult.fail("桥接执行失败: ${e.message}", "BRIDGE_ERROR")
        }
    }

    companion object {
        /**
         * 从 DEX 加载并创建适配器。
         * dexLoader 通常为 DefaultDexLoader。
         */
        fun create(dexClassLoader: java.lang.ClassLoader, className: String, apiBridge: ApiBridge): BridgeDexAdapter {
            val clazz = dexClassLoader.loadClass(className)
            val instance = clazz.getDeclaredConstructor().newInstance()
            val setBridge = clazz.getDeclaredMethod("setApiBridge", ApiBridge::class.java)
            setBridge.invoke(instance, apiBridge)
            val name = clazz.simpleName
            return BridgeDexAdapter(instance, name)
        }
    }
}
