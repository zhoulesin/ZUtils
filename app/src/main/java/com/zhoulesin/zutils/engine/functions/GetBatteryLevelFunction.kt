package com.zhoulesin.zutils.engine.functions

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.os.Build
import com.zhoulesin.zutils.engine.core.*
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

class GetBatteryLevelFunction : ZFunction {
    override val info = FunctionInfo(
        name = "getBatteryLevel",
        description = "Get the current battery level as a percentage (0-100)",
        permissions = emptyList(),
    )

    override suspend fun execute(context: ExecutionContext, args: JsonObject): ZResult {
        val batteryManager = context.androidContext.getSystemService(Context.BATTERY_SERVICE) as? BatteryManager
            ?: return ZResult.fail("Battery service not available", "SERVICE_UNAVAILABLE")

        val level = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            batteryManager.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)
        } else {
            @Suppress("DEPRECATION")
            val intent = context.androidContext.registerReceiver(null,
                IntentFilter(Intent.ACTION_BATTERY_CHANGED))
            if (intent != null) {
                val raw = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
                val scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, -1)
                if (raw >= 0 && scale > 0) (raw * 100) / scale else -1
            } else -1
        }

        if (level < 0) {
            return ZResult.fail("Unable to read battery level", "READ_FAILED")
        }

        return ZResult.Success(JsonPrimitive(level))
    }
}
