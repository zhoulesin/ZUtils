package com.zhoulesin.zutils.engine.functions

import android.os.Build
import com.zhoulesin.zutils.engine.core.*
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

class GetDeviceInfoFunction : ZFunction {
    override val info = FunctionInfo(
        name = "getDeviceInfo",
        description = "Get information about the Android device (brand, model, OS version, etc.)",
        outputType = OutputType.OBJECT,
    )

    override suspend fun execute(context: ExecutionContext, args: JsonObject): ZResult {
        val result = buildMap {
            put("brand", JsonPrimitive(Build.BRAND))
            put("model", JsonPrimitive(Build.MODEL))
            put("device", JsonPrimitive(Build.DEVICE))
            put("product", JsonPrimitive(Build.PRODUCT))
            put("manufacturer", JsonPrimitive(Build.MANUFACTURER))
            put("osVersion", JsonPrimitive(Build.VERSION.RELEASE))
            put("sdkLevel", JsonPrimitive(Build.VERSION.SDK_INT))
            put("hardware", JsonPrimitive(Build.HARDWARE))
        }
        return ZResult.Success(JsonObject(result))
    }
}
