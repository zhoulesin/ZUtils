package com.zhoulesin.zutils.engine.core

import android.content.Context
import com.zhoulesin.zutils.engine.permissions.PermissionChecker
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.serialization.json.JsonElement

class ExecutionContext(
    val androidContext: Context,
    val scope: CoroutineScope,
    val registry: FunctionRegistry,
    val permissionChecker: PermissionChecker? = null,
    val state: MutableStateFlow<Map<String, JsonElement>> = MutableStateFlow(emptyMap()),
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
