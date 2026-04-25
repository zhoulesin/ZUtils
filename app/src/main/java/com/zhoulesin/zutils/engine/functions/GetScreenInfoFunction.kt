package com.zhoulesin.zutils.engine.functions

import android.content.Context
import android.graphics.Point
import android.hardware.display.DisplayManager
import android.os.Build
import android.util.DisplayMetrics
import android.view.WindowManager
import com.zhoulesin.zutils.engine.core.*
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

class GetScreenInfoFunction : ZFunction {
    override val info = FunctionInfo(
        name = "getScreenInfo",
        description = "Get screen resolution, density, and refresh rate",
    )

    override suspend fun execute(context: ExecutionContext, args: JsonObject): ZResult {
        val wm = context.androidContext.getSystemService(Context.WINDOW_SERVICE) as? WindowManager
            ?: return ZResult.fail("Window service not available", "SERVICE_UNAVAILABLE")

        val metrics = DisplayMetrics()
        wm.defaultDisplay.getRealMetrics(metrics)

        val result = buildMap {
            put("widthPx", JsonPrimitive(metrics.widthPixels))
            put("heightPx", JsonPrimitive(metrics.heightPixels))
            put("densityDpi", JsonPrimitive(metrics.densityDpi))
            put("density", JsonPrimitive(metrics.density.toDouble()))
            put("scaledDensity", JsonPrimitive(metrics.scaledDensity.toDouble()))
            put("refreshRate", JsonPrimitive(wm.defaultDisplay.refreshRate.toDouble()))
        }
        return ZResult.Success(JsonObject(result))
    }
}
