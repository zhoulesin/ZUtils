package com.zhoulesin.zutils.engine.core

/**
 * FunctionRegistry 是 ZFunction 的注册中心。
 * 所有可执行的函数（包括内置函数、DEX 插件加载的函数）都必须先注册到这里，
 * 然后才能被 WorkflowEngine 发现并执行。
 */
interface FunctionRegistry {
    /** 注册一个函数。后注册的同名函数会覆盖之前的。 */
    fun register(function: ZFunction)

    /** 按名称注销一个函数。 */
    fun unregister(name: String)

    /** 按名称查找已注册的函数，未找到返回 null。 */
    fun get(name: String): ZFunction?

    /** 获取所有已注册函数的元信息列表（给 LLM 生成 function schema 用）。 */
    fun getAllInfos(): List<FunctionInfo>

    /** 检查指定名称的函数是否已注册。 */
    fun contains(name: String): Boolean

    /** 当前已注册的函数总数。 */
    fun size(): Int
}
