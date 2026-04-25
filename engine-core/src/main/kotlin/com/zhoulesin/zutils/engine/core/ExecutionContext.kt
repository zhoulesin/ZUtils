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
