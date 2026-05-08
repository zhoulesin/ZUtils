package com.zhoulesin.zutils.engine.core

import android.content.Context
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.serialization.json.JsonElement

class ExecutionContext(
    val androidContext: Context,
    val scope: CoroutineScope,
    val registry: FunctionRegistry,
    val state: MutableStateFlow<Map<String, JsonElement>> = MutableStateFlow(emptyMap()),
    /**
     * 当 [com.zhoulesin.zutils.engine.core.FunctionInfo.permissions] 中有已声明但未授予的项时调用。
     * 返回 `true` 表示用户已完成授权流程（引擎会再次校验）；`false` 表示用户拒绝，当前工作流步骤失败并中断。
     */
    val requestRuntimePermissions: (suspend (deniedPermissions: List<String>, forFunction: String) -> Boolean)? = null,
) {
    var cancelled: Boolean = false
        private set

    fun cancel() {
        cancelled = true
    }

    fun updateState(key: String, value: JsonElement) {
        state.value = state.value + (key to value)
    }
}
