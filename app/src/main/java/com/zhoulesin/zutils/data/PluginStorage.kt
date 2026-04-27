package com.zhoulesin.zutils.data

import android.content.Context
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File
import java.util.UUID

@Serializable
data class PluginInfo(
    val id: String,
    val name: String,
    val description: String,
    val icon: String = "",
    val version: String = "1.0.0",
    val author: String = "",
    val category: String = "工具",
    val stepsJson: String,
)

@Serializable
data class CataloguePlugin(
    val id: String,
    val name: String,
    val description: String,
    val icon: String,
    val category: String,
    val version: String,
    val author: String,
    val downloads: Int = 0,
    val stepsJson: String,
)

class PluginStorage(context: Context) {
    private val dir = File(context.filesDir, "plugins").also { it.mkdirs() }
    private val json = Json { prettyPrint = true }

    fun loadAll(): List<PluginInfo> {
        return dir.listFiles()
            ?.filter { it.name.endsWith(".json") }
            ?.mapNotNull { file -> try { json.decodeFromString<PluginInfo>(file.readText()) } catch (_: Exception) { null } }
            ?.sortedByDescending { it.name }
            ?: emptyList()
    }

    fun save(plugin: PluginInfo) {
        File(dir, "${plugin.id}.json").writeText(json.encodeToString(plugin))
    }

    fun delete(id: String) {
        File(dir, "$id.json").delete()
    }

    fun isInstalled(id: String): Boolean = File(dir, "$id.json").exists()
}

object PluginCatalogue {
    val plugins = listOf(
        CataloguePlugin(
            id = "weather-query",
            name = "天气查询",
            description = "查询指定城市的实时天气（温度、湿度、风力等）",
            icon = "🌤", category = "日常", version = "1.0.0", author = "ZUtils Team", downloads = 0,
            stepsJson = """[{"function":"getWeather","args":{"city":"北京"}}]""",
        ),
        CataloguePlugin(
            id = "morning-briefing",
            name = "早安播报",
            description = "每日自动播报时间、天气、日程",
            icon = "🌅", category = "日常", version = "1.0.0", author = "ZUtils Team", downloads = 1280,
            stepsJson = """[{"function":"getCurrentTime"},{"function":"toast","pipeline":{"message":"{0}"}}]""",
        ),
        CataloguePlugin(
            id = "weather-alert",
            name = "天气预警",
            description = "查询当天天气并提醒带伞或加衣",
            icon = "🌤", category = "日常", version = "1.0.0", author = "ZUtils Team", downloads = 956,
            stepsJson = """[{"function":"getDeviceInfo"},{"function":"toast","pipeline":{"message":"{0.model}"}}]""",
        ),
        CataloguePlugin(
            id = "system-report",
            name = "设备体检",
            description = "一键检查电量、存储、网络状态",
            icon = "📊", category = "工具", version = "1.0.0", author = "ZUtils Team", downloads = 723,
            stepsJson = """[{"function":"getBatteryLevel"},{"function":"getStorageInfo"},{"function":"getNetworkType"},{"function":"toast"}]""",
        ),
        CataloguePlugin(
            id = "clipboard-toolkit",
            name = "剪贴板工具集",
            description = "复制剪贴板内容，执行 Base64/翻译等操作",
            icon = "📋", category = "工具", version = "1.0.0", author = "ZUtils Team", downloads = 512,
            stepsJson = """[{"function":"getClipboard"},{"function":"base64","args":{"action":"encode","text":"hello"}}]""",
        ),
        CataloguePlugin(
            id = "uuid-generator",
            name = "UUID 生成器",
            description = "生成随机 UUID 并复制到剪贴板",
            icon = "🔑", category = "工具", version = "1.0.0", author = "ZUtils Team", downloads = 340,
            stepsJson = """[{"function":"uuid"},{"function":"setClipboard","pipeline":{"text":"{0}"}},{"function":"toast","pipeline":{"message":"{0}"}}]""",
        ),
    )
}

data class AppState(
    val installedPlugins: List<PluginInfo>,
    val cataloguePlugins: List<CataloguePlugin>,
)
