package com.zhoulesin.zutils.bridge

import android.content.Context
import java.lang.reflect.Modifier

/**
 * 通用反射桥——通过类名+方法名+实参调用任意安卓静态方法或实例方法。
 *
 * DEX 调用示例（创建文件夹）：
 *   val cls = Class.forName("java.io.File")
 *   val ctor = cls.getConstructor(String::class.java)
 *   val file = ctor.newInstance("/data/data/.../files/mydir")
 *   val ok = file::class.java.getMethod("mkdirs").invoke(file)
 *   return ok.toString()
 *
 * 实际上纯 JDK 反射调用不需要 bridge。
 * bridge 只用于无法在 DEX 中直接引用的 Android 类：
 *   bridge.callApi("android.widget.Toast", listOf("showToast", "appContext", "hello"))
 */
object AppApiBridge : ApiBridge {

    lateinit var appContext: Context

    fun init(context: Context) {
        appContext = context.applicationContext
    }

    /**
     * 反射调用安卓 API。
     * @param apiTag 全类名，如 "android.widget.Toast"
     * @param params [0]=方法名, [1..n]=实参。"appContext" 自动替换为 Application Context
     */
    override fun callApi(apiTag: String, params: List<String>): String {
        return try {
            val clazz = Class.forName(apiTag)
            val methodName = params.firstOrNull() ?: return "缺少方法名"
            val args = params.drop(1).map { if (it == "appContext") appContext else it }
            val argTypes = args.map { it::class.java }.toTypedArray()

            val method = clazz.methods.firstOrNull { m ->
                m.name == methodName && m.parameterCount == args.size
            } ?: clazz.declaredMethods.firstOrNull { m ->
                m.name == methodName && m.parameterCount == args.size
            } ?: return "未找到方法 $methodName(${args.size}参数)"

            val isStatic = Modifier.isStatic(method.modifiers)
            val instance = if (isStatic) null else appContext
            val result = method.invoke(instance, *args.toTypedArray())
            result?.toString() ?: "null"
        } catch (e: Exception) {
            "反射失败(${e::class.java.simpleName}): ${e.message}"
        }
    }
}
