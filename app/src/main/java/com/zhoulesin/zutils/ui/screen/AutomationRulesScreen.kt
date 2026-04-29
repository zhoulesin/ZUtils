package com.zhoulesin.zutils.ui.screen

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.unit.dp
import com.zhoulesin.zutils.data.AutomationRule
import com.zhoulesin.zutils.engine.AutomationEngine
import com.zhoulesin.zutils.ui.theme.RaycastBlueTransparent
import com.zhoulesin.zutils.ui.theme.RaycastCardSurface
import com.zhoulesin.zutils.ui.theme.RaycastWhiteBorder06
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
        Text("自动化规则", style = MaterialTheme.typography.titleLarge)
        Spacer(Modifier.height(4.dp))
        Text("管理定时执行的自动化规则", style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant)

        Spacer(Modifier.height(12.dp))

        if (rules.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("还没有自动化规则\n对助手说\"每天8点…\"来创建", style = MaterialTheme.typography.bodyMedium,
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
            containerColor = if (enabled) MaterialTheme.colorScheme.surface
                else MaterialTheme.colorScheme.surface.copy(alpha = 0.65f)
        ),
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            RaycastWhiteBorder06.copy(alpha = if (enabled) 1f else 0.6f),
        ),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 11.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(rule.name, style = MaterialTheme.typography.titleSmall)
                    Spacer(Modifier.width(6.dp))
                    Surface(
                        color = RaycastCardSurface,
                        shape = RoundedCornerShape(6.dp),
                        border = androidx.compose.foundation.BorderStroke(1.dp, RaycastWhiteBorder06),
                    ) {
                        Text(
                            rule.cron,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                            style = MaterialTheme.typography.labelSmall,
                            fontFamily = FontFamily.Monospace,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                    }
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
                },
                colors = SwitchDefaults.colors(
                    checkedThumbColor = MaterialTheme.colorScheme.onPrimary,
                    checkedTrackColor = MaterialTheme.colorScheme.primary,
                    uncheckedTrackColor = RaycastBlueTransparent,
                ),
            )
        }
    }
}
