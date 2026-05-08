package com.zhoulesin.zutils.engine.functions

import android.os.Environment
import android.os.StatFs
import com.zhoulesin.zutils.engine.core.*
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

class GetStorageInfoFunction : ZFunction {
    override val info = FunctionInfo(
        name = "getStorageInfo",
        description = "Get internal storage total and available space in GB",
        outputType = OutputType.OBJECT,
    )

    override suspend fun execute(context: ExecutionContext, args: JsonObject): ZResult {
        val path = Environment.getDataDirectory()
        val stat = StatFs(path.path)

        val blockSize = stat.blockSizeLong
        val totalBlocks = stat.blockCountLong
        val availableBlocks = stat.availableBlocksLong

        val totalGB = (totalBlocks * blockSize) / (1024.0 * 1024.0 * 1024.0)
        val freeGB = (availableBlocks * blockSize) / (1024.0 * 1024.0 * 1024.0)
        val usedGB = totalGB - freeGB

        val result = buildMap {
            put("totalGB", JsonPrimitive(String.format("%.1f", totalGB).toDouble()))
            put("usedGB", JsonPrimitive(String.format("%.1f", usedGB).toDouble()))
            put("freeGB", JsonPrimitive(String.format("%.1f", freeGB).toDouble()))
            put("freePercent", JsonPrimitive(
                if (totalGB > 0) ((freeGB / totalGB) * 100).toInt() else 0
            ))
        }
        return ZResult.Success(JsonObject(result))
    }
}
