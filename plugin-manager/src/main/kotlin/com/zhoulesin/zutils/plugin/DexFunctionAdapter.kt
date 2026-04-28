package com.zhoulesin.zutils.plugin

import android.util.Log
import com.zhoulesin.zutils.engine.core.*
import com.zhoulesin.zutils.engine.dex.DexSpec
import kotlinx.serialization.json.*

private const val TAG = "ZUtils-DEX"

class DexFunctionAdapter(
    private val target: Any,
    private val spec: DexSpec,
) : ZFunction {

    private val handleMethod = target.javaClass.getMethod("handle", String::class.java)

    override val info: FunctionInfo
        get() = FunctionInfo(
            name = spec.functionName,
            description = spec.description,
            parameters = spec.parameters,
            source = FunctionSource.DEX,
            permissions = spec.requiredPermissions,
            outputType = deriveOutputType(spec),
        )

    override suspend fun execute(context: ExecutionContext, args: JsonObject): ZResult {
        return try {
            val input = buildJsonObject {
                putJsonObject("args") {
                    for ((key, value) in args) {
                        put(key, value)
                    }
                }
            }
            val jsonInput = Json.encodeToString(input)
            val jsonOutput = handleMethod.invoke(target, jsonInput) as String
            val output = Json.parseToJsonElement(jsonOutput).jsonObject

            if (output.containsKey("error")) {
                val message = output["error"]?.jsonPrimitive?.contentOrNull ?: "Unknown error"
                val code = output["code"]?.jsonPrimitive?.contentOrNull
                return ZResult.Error(message = message, code = code)
            }

            val result = output["result"]
            if (result != null) {
                ZResult.Success(result)
            } else {
                ZResult.Success(JsonPrimitive(jsonOutput))
            }
        } catch (e: Exception) {
            Log.e(TAG, "DexFunctionAdapter execute failed", e)
            ZResult.Error(message = "Plugin execution error: ${e.message}", code = "EXEC_FAILED")
        }
    }
}

private fun deriveOutputType(spec: DexSpec): OutputType {
    return OutputType.OBJECT
}
