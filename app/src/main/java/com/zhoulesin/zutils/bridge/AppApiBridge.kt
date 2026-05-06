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
            val methodName = params.firstOrNull() ?: return "缺少方法名"
            val rawArgs = params.drop(1).map { if (it == "appContext") appContext else it }

            // 按参数个数匹配方法
            val method = clazz.methods.firstOrNull { it.name == methodName && it.parameterCount == rawArgs.size }
                ?: clazz.declaredMethods.firstOrNull { it.name == methodName && it.parameterCount == rawArgs.size }
                ?: return "未找到方法 $methodName(${rawArgs.size}参数)"

            // 按方法声明的参数类型转换实参
            val typedArgs = rawArgs.mapIndexed { i, arg ->
                if (arg === appContext) arg else convertArg(arg, method.parameterTypes[i])
            }.toTypedArray()

            val isStatic = Modifier.isStatic(method.modifiers)
            val instance = if (isStatic) null else appContext
            val result = method.invoke(instance, *typedArgs)

            // 自动调用 show()（适配 Toast、Dialog 等 builder 模式，切回主线程）
            if (result != null) {
                try {
                    val showMethod = result::class.java.getMethod("show")
                    Handler(Looper.getMainLooper()).post { showMethod.invoke(result) }
                } catch (_: Exception) {}
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
