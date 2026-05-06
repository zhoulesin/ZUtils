package com.zhoulesin.zutils.bridge

import android.content.Context
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

            val method = clazz.methods.firstOrNull { it.name == methodName && it.parameterCount == rawArgs.size }
                ?: clazz.declaredMethods.firstOrNull { it.name == methodName && it.parameterCount == rawArgs.size }
                ?: return "未找到方法 $methodName(${rawArgs.size}参数)"

            val typedArgs = rawArgs.mapIndexed { i, arg ->
                if (arg === appContext) arg else convertArg(arg, method.parameterTypes[i])
            }.toTypedArray()

            val isStatic = Modifier.isStatic(method.modifiers)
            val instance = if (isStatic) null else appContext
            method.invoke(instance, *typedArgs)
        } catch (e: Exception) {
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
