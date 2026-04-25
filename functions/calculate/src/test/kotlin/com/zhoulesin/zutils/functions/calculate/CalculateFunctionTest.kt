package com.zhoulesin.zutils.functions.calculate

import android.content.Context
import com.zhoulesin.zutils.engine.core.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import org.junit.Assert.*
import org.junit.Test
import org.mockito.Mockito.mock

class CalculateFunctionTest {

    private val function = CalculateFunction()

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
    fun `addition`() = runBlocking {
        val r = function.execute(ctx(), JsonObject(mapOf("expression" to JsonPrimitive("2+2"))))
        assertTrue(r is ZResult.Success)
        assertEquals("4.0", (r as ZResult.Success).data.toString())
    }

    @Test
    fun `operator precedence`() = runBlocking {
        val r = function.execute(ctx(), JsonObject(mapOf("expression" to JsonPrimitive("2+3*4"))))
        assertTrue(r is ZResult.Success)
        assertEquals("14.0", (r as ZResult.Success).data.toString())
    }

    @Test
    fun `parentheses`() = runBlocking {
        val r = function.execute(ctx(), JsonObject(mapOf("expression" to JsonPrimitive("(2+3)*4"))))
        assertTrue(r is ZResult.Success)
        assertEquals("20.0", (r as ZResult.Success).data.toString())
    }

    @Test
    fun `division`() = runBlocking {
        val r = function.execute(ctx(), JsonObject(mapOf("expression" to JsonPrimitive("10/2"))))
        assertTrue(r is ZResult.Success)
        assertEquals("5.0", (r as ZResult.Success).data.toString())
    }

    @Test
    fun `invalid input`() = runBlocking {
        val r = function.execute(ctx(), JsonObject(mapOf("expression" to JsonPrimitive("abc"))))
        assertTrue(r is ZResult.Error)
    }

    @Test
    fun `missing arg`() = runBlocking {
        val r = function.execute(ctx(), JsonObject(emptyMap()))
        assertTrue(r is ZResult.Error)
    }
}
