package com.zhoulesin.zutils.bridge

import android.content.Context
import android.os.Handler
import android.os.Looper
import com.zhoulesin.zutils.engine.bridge.ApiBridge
import java.lang.reflect.Modifier

object AppApiBridge : ApiBridge {

    lateinit var appContext: Context

    fun init(context: Context) {
        appContext = context.applicationContext
    }

    override fun callApi(apiTag: String, params: List<String>): String {
        return try {
            val clazz = Class.forName(apiTag)
            val rawFirst = params.firstOrNull() ?: return "缺少方法名"
            val parts = rawFirst.split(":")
            val methodName = parts[0]
            val autoExec = parts.getOrNull(1)  // "makeText:show" → autoExec="show"

            val rawArgs = params.drop(1).map { if (it == "appContext") appContext else it }

            val method = clazz.methods.firstOrNull { it.name == methodName && it.parameterCount == rawArgs.size }
                ?: clazz.declaredMethods.firstOrNull { it.name == methodName && it.parameterCount == rawArgs.size }
                ?: return "未找到方法 $methodName(${rawArgs.size}参数)"

            val typedArgs = rawArgs.mapIndexed { i, arg ->
                if (arg === appContext) arg else convertArg(arg, method.parameterTypes[i])
            }.toTypedArray()

            val isStatic = Modifier.isStatic(method.modifiers)
            val instance = if (isStatic) null else appContext
            val result = method.invoke(instance, *typedArgs)

            // :show → 在主线程调 result.show()
            if (autoExec != null && result != null) {
                try {
                    val m = result::class.java.getMethod(autoExec)
                    if (m.parameterCount == 0) {
                        Handler(Looper.getMainLooper()).post { m.invoke(result) }
                    }
                } catch (_: NoSuchMethodException) {
                    return "方法 $methodName 执行成功，但后续 $autoExec 调用失败：对象无此无参方法"
                }
            }

            result?.toString() ?: "null"
        } catch (e: Exception) {
            "反射失败(${e::class.java.simpleName}): ${e.message}"
        }
    }

    private fun convertArg(value: Any, target: Class<*>): Any = when (target) {
        Int::class.java, Integer::class.java -> (value as String).toIntOrNull() ?: 0
        Long::class.java -> (value as String).toLongOrNull() ?: 0L
        Float::class.java -> (value as String).toFloatOrNull() ?: 0f
        Double::class.java -> (value as String).toDoubleOrNull() ?: 0.0
        Boolean::class.java -> (value as String).toBoolean()
        CharSequence::class.java -> value as String
        else -> value
    }
}
