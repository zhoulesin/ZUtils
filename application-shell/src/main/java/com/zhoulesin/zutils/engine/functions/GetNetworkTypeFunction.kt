package com.zhoulesin.zutils.engine.functions

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Build
import com.zhoulesin.zutils.engine.core.*
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

class GetNetworkTypeFunction : ZFunction {
    override val info = FunctionInfo(
        name = "getNetworkType",
        description = "Check current network type: wifi, mobile, or none",
        permissions = listOf(android.Manifest.permission.ACCESS_NETWORK_STATE),
        outputType = OutputType.OBJECT,
    )

    override suspend fun execute(context: ExecutionContext, args: JsonObject): ZResult {
        val cm = context.androidContext.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
            ?: return ZResult.fail("Connectivity service not available", "SERVICE_UNAVAILABLE")

        val network = cm.activeNetwork ?: return ZResult.ok("none")
        val caps = cm.getNetworkCapabilities(network) ?: return ZResult.ok("none")

        val type = when {
            caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> "wifi"
            caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> "mobile"
            caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> "ethernet"
            else -> "unknown"
        }

        val result = buildMap {
            put("type", JsonPrimitive(type))
            put("connected", JsonPrimitive(true))
        }
        return ZResult.Success(JsonObject(result))
    }
}
