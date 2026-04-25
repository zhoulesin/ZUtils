package com.zhoulesin.zutils.functions.uuid

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

class UuidFunctionTest {

    private val function = UuidFunction()

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
    fun `valid UUID format`() = runBlocking {
        val r = function.execute(ctx(), JsonObject(emptyMap()))
        assertTrue(r is ZResult.Success)
        val uuid = (r as ZResult.Success).data.toString().removeSurrounding("\"")
        assertTrue(uuid.matches(Regex("[a-f0-9]{8}-[a-f0-9]{4}-4[a-f0-9]{3}-[89ab][a-f0-9]{3}-[a-f0-9]{12}")))
    }

    @Test
    fun `unique values`() = runBlocking {
        val r1 = (function.execute(ctx(), JsonObject(emptyMap())) as ZResult.Success).data.toString()
        val r2 = (function.execute(ctx(), JsonObject(emptyMap())) as ZResult.Success).data.toString()
        assertNotEquals(r1, r2)
    }
}
