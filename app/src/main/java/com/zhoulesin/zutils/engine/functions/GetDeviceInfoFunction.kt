package com.zhoulesin.zutils.engine.functions

import android.os.Build
import com.zhoulesin.zutils.engine.core.*
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonPrimitive

class GetDeviceInfoFunction : ZFunction {
    override val info = FunctionInfo(
        name = "getDeviceInfo",
        description = "Get information about the Android device (brand, model, OS version, etc.)",
        parameters = listOf(
            Parameter(
                name = "fields",
                description = "Optional list of fields to retrieve. Returns all if omitted.",
                type = ParameterType.ARRAY,
                required = false,
            )
        ),
    )

    override suspend fun execute(context: ExecutionContext, args: JsonObject): ZResult {
        val allInfo = mapOf(
            "brand" to JsonPrimitive(Build.BRAND),
            "model" to JsonPrimitive(Build.MODEL),
            "device" to JsonPrimitive(Build.DEVICE),
            "product" to JsonPrimitive(Build.PRODUCT),
            "manufacturer" to JsonPrimitive(Build.MANUFACTURER),
            "osVersion" to JsonPrimitive(Build.VERSION.RELEASE),
            "sdkLevel" to JsonPrimitive(Build.VERSION.SDK_INT),
            "hardware" to JsonPrimitive(Build.HARDWARE),
        )

        val fields = args["fields"]?.jsonArray?.map { it.jsonPrimitive.content }

        val result = if (fields != null) {
            allInfo.filterKeys { it in fields }
        } else {
            allInfo
        }

        return ZResult.Success(JsonObject(result))
    }
}
