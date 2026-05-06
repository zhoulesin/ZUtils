package com.zhoulesin.zutils.bridge

import android.content.Context
import android.util.Log
import com.zhoulesin.zutils.bridge.AppApiBridge.appContext
import com.zhoulesin.zutils.engine.bridge.ApiBridge
import java.lang.reflect.Modifier

object AppApiBridge : ApiBridge {

    lateinit var appContext: Context

    fun init(context: Context) {
        appContext = context.applicationContext
    }

    override fun callApi(apiTag: String, params: List<String>): Any? {
        return try {
            val clazz = Class.forName(apiTag)
            val methodName = params.firstOrNull() ?: return "缺少方法名"
            val rawArgs = params.drop(1).map { if (it == "appContext") appContext else it }

            Log.i("ZUtils-Bridge", "callApi class=$apiTag method=$methodName argCount=${rawArgs.size}")
            for ((i, a) in rawArgs.withIndex()) {
                Log.i("ZUtils-Bridge", "  arg[$i]: type=${a::class.java.simpleName} value=$a")
            }

            // 按参数类型匹配方法（优先精确匹配，避免 count 相同但选错重载）
            val candidates = (clazz.methods + clazz.declaredMethods)
                .filter { it.name == methodName && it.parameterCount == rawArgs.size }

            Log.i("ZUtils-Bridge", "  candidates count=${candidates.size}")
            for (c in candidates) {
                val sig = c.parameterTypes.joinToString(",") { it.simpleName }
                Log.i("ZUtils-Bridge", "  candidate: $sig")
            }

            val method = candidates.maxByOrNull { m ->
                rawArgs.indices.sumOf { i ->
                    val raw = rawArgs[i]; val pt = m.parameterTypes[i]
                    when {
                        raw === appContext && pt == Context::class.java -> 3
                        pt.isAssignableFrom(raw::class.java) -> 2
                        pt.isPrimitive() -> 1
                        else -> 0
                    }
                }
            }

            if (method == null) {
                Log.w("ZUtils-Bridge", "  no matching method found")
                return "未找到方法 $methodName(${rawArgs.size}参数)"
            }

            val sig = method.parameterTypes.joinToString(",") { it.simpleName }
            Log.i("ZUtils-Bridge", "  selected: $sig")

            val typedArgs = rawArgs.mapIndexed { i, arg ->
                if (arg === appContext) {
                    Log.i("ZUtils-Bridge", "  typed[$i]=appContext")
                    arg
                } else {
                    val converted = convertArg(arg, method.parameterTypes[i])
                    Log.i("ZUtils-Bridge", "  typed[$i]=${converted::class.java.simpleName}($converted)")
                    converted
                }
            }.toTypedArray()

            val isStatic = Modifier.isStatic(method.modifiers)
            val instance = if (isStatic) null else appContext
            val result = method.invoke(instance, *typedArgs)
            Log.i("ZUtils-Bridge", "  result type=${result?.let { it::class.java.simpleName } ?: "null"}")

            result
        } catch (e: java.lang.reflect.InvocationTargetException) {
            val cause = e.cause
            Log.w("ZUtils-Bridge", "  InvocationTargetException cause=${cause?.let { it::class.java.simpleName }}:${cause?.message}")
            "反射失败(${cause?.let { it::class.java.simpleName }}): ${cause?.message ?: "无消息"}"
        } catch (e: Exception) {
            Log.w("ZUtils-Bridge", "  error: ${e::class.java.simpleName}: ${e.message}")
            "反射失败(${e::class.java.simpleName}): ${e.message}"
        }
    }

    private fun convertArg(value: Any, target: Class<*>): Any = when (target) {
        Int::class.java, Int::class.javaPrimitiveType, Integer::class.java ->
            (value as String).toIntOrNull() ?: 0
        Long::class.java, Long::class.javaPrimitiveType ->
            (value as String).toLongOrNull() ?: 0L
        Float::class.java, Float::class.javaPrimitiveType ->
            (value as String).toFloatOrNull() ?: 0f
        Double::class.java, Double::class.javaPrimitiveType ->
            (value as String).toDoubleOrNull() ?: 0.0
        Boolean::class.java, Boolean::class.javaPrimitiveType ->
            (value as String).toBoolean()
        CharSequence::class.java -> value as String
        else -> value
    }
}
