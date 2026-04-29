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

data class AppState(
    val installedPlugins: List<PluginInfo>,
    val cataloguePlugins: List<CataloguePlugin>,
)
