package com.zhoulesin.zutils.ui.screen

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import com.zhoulesin.zutils.data.AutomationRule
import com.zhoulesin.zutils.engine.AutomationEngine
import kotlinx.coroutines.launch

@Composable
fun AutomationRulesScreen(
    autoEngine: AutomationEngine,
    modifier: Modifier = Modifier,
) {
    var rules by remember { mutableStateOf<List<AutomationRule>>(emptyList()) }
    val scope = rememberCoroutineScope()

    fun refresh() {
        scope.launch { rules = autoEngine.loadAll() }
    }

    LaunchedEffect(Unit) { refresh() }

    Column(modifier = modifier.fillMaxSize().padding(16.dp)) {
        Text("⚡ 自动化", style = MaterialTheme.typography.titleLarge)
        Spacer(Modifier.height(4.dp))
        Text("管理定时执行的自动化规则", style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant)

        Spacer(Modifier.height(12.dp))

        if (rules.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("还没有自动化规则\n对助手说"每天8点…"来创建", style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(rules, key = { it.id }) { rule ->
                    AutomationRuleCard(rule = rule, autoEngine = autoEngine, onChanged = { refresh() })
                }
            }
        }
    }
}

@Composable
private fun AutomationRuleCard(
    rule: AutomationRule,
    autoEngine: AutomationEngine,
    onChanged: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    var enabled by remember(rule) { mutableStateOf(rule.isEnabled) }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (enabled) MaterialTheme.colorScheme.surfaceVariant
                else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        ),
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(rule.name, style = MaterialTheme.typography.titleSmall)
                    Spacer(Modifier.width(6.dp))
                    Text(rule.cron, style = MaterialTheme.typography.labelSmall,
                        fontFamily = FontFamily.Monospace,
                        color = MaterialTheme.colorScheme.primary)
                }
                Text(rule.stepsJson.take(100).replace("\n", " ") + if (rule.stepsJson.length > 100) "…" else "",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1)
            }
            Switch(
                checked = enabled,
                onCheckedChange = { v ->
                    enabled = v
                    scope.launch { autoEngine.toggle(rule.id, v); onChanged() }
                }
            )
        }
    }
}
