package com.zhoulesin.zutils.ui.screen

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import com.zhoulesin.zutils.engine.core.FunctionInfo
import com.zhoulesin.zutils.engine.core.FunctionSource

@Composable
fun FunctionsScreen(functions: List<FunctionInfo>, modifier: Modifier = Modifier) {
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        items(functions) { info ->
            FunctionCard(info)
        }
    }
}

@Composable
private fun FunctionCard(info: FunctionInfo) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
        ),
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    text = info.name,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
                SourceBadge(info.source)
            }

            Spacer(Modifier.height(4.dp))
            Text(
                text = info.description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            if (info.parameters.isNotEmpty()) {
                Spacer(Modifier.height(8.dp))
                Text(
                    text = "参数:",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                info.parameters.forEach { param ->
                    val required = if (param.required) " *" else ""
                    Text(
                        text = "  ${param.name}: ${param.type}$required — ${param.description}",
                        style = MaterialTheme.typography.bodySmall,
                        fontFamily = FontFamily.Monospace,
                    )
                }
            }

            if (info.permissions.isNotEmpty()) {
                Spacer(Modifier.height(4.dp))
                Text(
                    text = "权限: ${info.permissions.joinToString(", ")}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }
        }
    }
}

@Composable
private fun SourceBadge(source: FunctionSource) {
    val (text, color) = when (source) {
        FunctionSource.BUILT_IN -> "内置" to MaterialTheme.colorScheme.tertiary
        FunctionSource.DEX -> "DEX" to MaterialTheme.colorScheme.secondary
        FunctionSource.REMOTE -> "远端" to MaterialTheme.colorScheme.error
        FunctionSource.USER_DEFINED -> "自定义" to MaterialTheme.colorScheme.primary
    }
    Surface(
        shape = MaterialTheme.shapes.small,
        color = color.copy(alpha = 0.2f),
    ) {
        Text(
            text = text,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
            style = MaterialTheme.typography.labelSmall,
            color = color,
        )
    }
}
