package com.zhoulesin.zutils.mcp

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit

@Serializable
data class McpToolInfo(
    val name: String,
    val description: String = "",
)

object McpKnownTools {
    val ALL: Set<String> = setOf(
        "weather_current", "translate_text", "news_headlines",
        "geo_location", "qrcode_generate", "web_search",
        "email_send", "document_summarize",
    )
}

class McpClient(
    private val baseUrl: String = "http://10.0.2.2:8080",
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build(),
    private val json: Json = Json { ignoreUnknownKeys = true },
) {

    suspend fun callTool(tool: String, args: JsonObject): String = withContext(Dispatchers.IO) {
        val bodyJson = buildJsonObject {
            put("tool", tool)
            put("arguments", args)
        }
        val request = Request.Builder()
            .url("$baseUrl/api/v1/mcp/call")
            .post(bodyJson.toString().toRequestBody("application/json".toMediaType()))
            .build()
        val response = client.newCall(request).execute()
        val body = response.body?.string() ?: ""
        val root = json.parseToJsonElement(body).jsonObject
        root["data"]?.jsonObject?.get("output")?.jsonPrimitive?.contentOrNull ?: body
    }

    suspend fun listTools(): List<McpToolInfo> = withContext(Dispatchers.IO) {
        try {
            val url = java.net.URL("$baseUrl/api/v1/mcp/tools")
            val result = url.readText()
            val root = json.parseToJsonElement(result).jsonObject
            root["data"]?.jsonArray?.map { el ->
                val obj = el.jsonObject
                McpToolInfo(
                    name = obj["name"]?.jsonPrimitive?.contentOrNull ?: "",
                    description = obj["description"]?.jsonPrimitive?.contentOrNull ?: "",
                )
            } ?: emptyList()
        } catch (_: Exception) {
            emptyList()
        }
    }
}
