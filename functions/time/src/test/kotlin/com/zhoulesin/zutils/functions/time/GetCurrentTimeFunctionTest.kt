package com.zhoulesin.zutils.functions.time

import android.content.Context
import com.zhoulesin.zutils.engine.core.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.JsonObject
import org.junit.Assert.*
import org.junit.Test
import org.mockito.Mockito.mock

class GetCurrentTimeFunctionTest {

    private val function = GetCurrentTimeFunction()

    private fun ctx() = ExecutionContext(
        androidContext = mock(Context::class.java),
        scope = CoroutineScope(Dispatchers.Default),
        registry = object : FunctionRegistry {
            override fun register(function: ZFunction) {}
            override fun unregister(name: String) {}
            override fun get(name: String): ZFunction? = null
            override fun getAllInfos(): List<FunctionInfo> = emptyList()
            override fun contains(name: String): Boolean = false
            override fun size(): Int = 0
        },
        state = MutableStateFlow(emptyMap()),
    )

    @Test
    fun `returns non-empty string`() = runBlocking {
        val r = function.execute(ctx(), JsonObject(emptyMap()))
        assertTrue(r is ZResult.Success)
        assertTrue((r as ZResult.Success).data.toString().removeSurrounding("\"").isNotEmpty())
    }

    @Test
    fun `default format is yyyy-MM-dd HH-mm-ss`() = runBlocking {
        val r = function.execute(ctx(), JsonObject(emptyMap()))
        assertTrue(r is ZResult.Success)
        val time = (r as ZResult.Success).data.toString().removeSurrounding("\"")
        assertTrue(time.matches(Regex("\\d{4}-\\d{2}-\\d{2} \\d{2}:\\d{2}:\\d{2}")))
    }
}
