package com.zhoulesin.zutils.ui.screen

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.zhoulesin.zutils.data.CataloguePlugin
import com.zhoulesin.zutils.data.PluginInfo
import com.zhoulesin.zutils.data.PluginStorage

@Composable
fun PluginsScreen(
    installed: List<PluginInfo>,
    catalogue: List<CataloguePlugin>,
    storage: PluginStorage,
    onInstall: (CataloguePlugin) -> Unit,
    onUninstall: (PluginInfo) -> Unit,
    onExecute: (PluginInfo) -> Unit,
    onNewWorkflow: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    var tab by remember { mutableStateOf(0) }

    Column(modifier = modifier.fillMaxSize().padding(16.dp)) {
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            item {
                FilterChip(selected = tab == 0, onClick = { tab = 0 }, label = { Text("已安装 (${installed.size})") })
            }
            item {
                FilterChip(selected = tab == 1, onClick = { tab = 1 }, label = { Text("市场") })
            }
            item {
                FilterChip(selected = tab == 2, onClick = { tab = 2 }, label = { Text("创建工作流") })
            }
        }

        Spacer(Modifier.height(8.dp))

        when (tab) {
            0 -> InstalledPluginsTab(installed, onUninstall, onExecute)
            1 -> MarketTab(catalogue, storage, onInstall)
            2 -> {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = androidx.compose.ui.Alignment.Center) {
                    Button(onClick = onNewWorkflow) { Text("打开工作流构建器") }
                }
            }
        }
    }
}

@Composable
private fun InstalledPluginsTab(
    plugins: List<PluginInfo>,
    onUninstall: (PluginInfo) -> Unit,
    onExecute: (PluginInfo) -> Unit,
) {
    if (plugins.isEmpty()) {
        Text("还没有安装插件，去市场看看", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    } else {
        LazyColumn(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            items(plugins) { plugin ->
                Card(
                    modifier = Modifier.fillMaxWidth().clickable { onExecute(plugin) },
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(plugin.icon, fontSize = MaterialTheme.typography.titleLarge.fontSize)
                        Spacer(Modifier.width(8.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(plugin.name, style = MaterialTheme.typography.titleSmall)
                            Text(plugin.description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        TextButton(onClick = { onUninstall(plugin) }) { Text("卸载", color = MaterialTheme.colorScheme.error) }
                    }
                }
            }
        }
    }
}

@Composable
private fun MarketTab(
    catalogue: List<CataloguePlugin>,
    storage: PluginStorage,
    onInstall: (CataloguePlugin) -> Unit,
) {
    LazyColumn(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        items(catalogue) { plugin ->
            val installed = storage.isInstalled(plugin.id)
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
            ) {
                Row(
                    modifier = Modifier.padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(plugin.icon, fontSize = MaterialTheme.typography.titleLarge.fontSize)
                    Spacer(Modifier.width(8.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(plugin.name, style = MaterialTheme.typography.titleSmall)
                        Text(plugin.description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text("${plugin.author} · ${plugin.version} · ${plugin.downloads}次下载", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    if (installed) {
                        Text("已安装", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.tertiary)
                    } else {
                        Button(onClick = { onInstall(plugin) }) { Text("安装") }
                    }
                }
            }
        }
    }
}
