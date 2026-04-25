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
import com.zhoulesin.zutils.engine.core.FunctionInfo
import com.zhoulesin.zutils.engine.core.OutputType
import com.zhoulesin.zutils.engine.core.ParameterType
import com.zhoulesin.zutils.engine.workflow.Workflow
import com.zhoulesin.zutils.engine.workflow.WorkflowStep
import com.zhoulesin.zutils.data.SavedStep
import com.zhoulesin.zutils.data.SavedWorkflow
import com.zhoulesin.zutils.data.WorkflowStorage
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WorkflowBuilderScreen(
    functions: List<FunctionInfo>,
    onSave: (SavedWorkflow) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var isPipeline by remember { mutableStateOf(false) }
    var title by remember { mutableStateOf("") }
    var desc by remember { mutableStateOf("") }
    val selectedSteps = remember { mutableStateListOf<FunctionInfo>() }
    var titleError by remember { mutableStateOf(false) }

    val availableFunctions = remember(selectedSteps.toList(), isPipeline) {
        if (selectedSteps.isEmpty()) {
            functions
        } else if (!isPipeline) {
            functions.filter { it.parameters.none { p -> p.required } }
        } else {
            val prev = selectedSteps.last().outputType
            functions.filter { isCompatible(prev, it) }
        }
    }

    Column(modifier = modifier.fillMaxSize()) {
        TopAppBar(
            title = { Text("创建工作流") },
            navigationIcon = {
                TextButton(onClick = onBack) { Text("← 返回") }
            },
            colors = TopAppBarDefaults.topAppBarColors(
                containerColor = MaterialTheme.colorScheme.primaryContainer,
            ),
        )

        LazyColumn(
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            item {
                Spacer(Modifier.height(4.dp))

                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it; titleError = false },
                    label = { Text("标题 *") },
                    placeholder = { Text("例如：系统诊断") },
                    isError = titleError,
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )

                Spacer(Modifier.height(8.dp))

                OutlinedTextField(
                    value = desc,
                    onValueChange = { desc = it },
                    label = { Text("描述（可选）") },
                    placeholder = { Text("例如：检查设备各项状态") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )

                Spacer(Modifier.height(8.dp))

                Text("执行模式:", style = MaterialTheme.typography.labelSmall)
                Spacer(Modifier.height(4.dp))
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    item {
                        FilterChip(
                            selected = !isPipeline,
                            onClick = { isPipeline = false; selectedSteps.clear() },
                            label = { Text("顺序 — 独立执行，互不传递") },
                        )
                    }
                    item {
                        FilterChip(
                            selected = isPipeline,
                            onClick = { isPipeline = true; selectedSteps.clear() },
                            label = { Text("管道 — 上一步结果传给下一步") },
                        )
                    }
                }
            }

            if (selectedSteps.isNotEmpty()) {
                item {
                    Spacer(Modifier.height(4.dp))
                    Text("已选步骤（点击移除）:", style = MaterialTheme.typography.labelSmall)
                    Spacer(Modifier.height(4.dp))
                }
                item {
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        items(selectedSteps.toList()) { fn ->
                            InputChip(
                                selected = false,
                                onClick = { selectedSteps.remove(fn) },
                                label = {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(fn.name)
                                        if (isPipeline && selectedSteps.size > 1) {
                                            Text(" → ", style = MaterialTheme.typography.labelSmall)
                                        }
                                    }
                                },
                                trailingIcon = { Text("✕") },
                            )
                        }
                    }
                }
                item {
                    Text(
                        text = if (isPipeline) {
                            val output = selectedSteps.last().outputType
                            "输出: $output → 筛选匹配入参的函数"
                        } else {
                            "顺序模式: 所有函数可用"
                        },
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            item {
                Spacer(Modifier.height(4.dp))
                Text(
                    text = if (selectedSteps.isEmpty()) "选择第一个函数:"
                        else if (isPipeline) "可用的下一步函数（根据输出类型匹配）:"
                        else "可用函数（仅显示无必填参数的）:",
                    style = MaterialTheme.typography.labelSmall,
                )
            }

            items(availableFunctions) { fn ->
                FunctionPickerItem(
                    info = fn,
                    onClick = { selectedSteps.add(fn) },
                )
            }
        }

        Spacer(Modifier.height(12.dp))
        Button(
            onClick = {
                if (title.isBlank()) {
                    titleError = true
                    return@Button
                }
                val workflow = buildWorkflow(
                    steps = selectedSteps.toList(),
                    isPipeline = isPipeline,
                    title = title.trim(),
                    description = desc.trim().ifEmpty { null },
                )
                val saved = SavedWorkflow(
                    id = WorkflowStorage.buildId(),
                    title = title.trim(),
                    description = desc.trim(),
                    type = if (isPipeline) "PIPELINE" else "SEQUENTIAL",
                    steps = workflow.steps.map { step ->
                        SavedStep(
                            function = step.function,
                            args = step.args,
                            pipeline = step.pipeline,
                        )
                    },
                    stepCount = workflow.steps.size,
                )
                onSave(saved)
                selectedSteps.clear()
                onBack()
            },
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            enabled = selectedSteps.isNotEmpty() && title.isNotBlank(),
        ) {
            Text("保存工作流（${selectedSteps.size} 步）")
        }
        Spacer(Modifier.height(12.dp))
    }
}

private fun isCompatible(prevOutput: OutputType, next: FunctionInfo): Boolean {
    val hasRequired = next.parameters.any { it.required }
    if (!hasRequired && next.parameters.any { it.type == ParameterType.STRING }) return true
    return when (prevOutput) {
        OutputType.TEXT -> next.parameters.any { it.type == ParameterType.STRING }
        OutputType.NUMBER -> next.parameters.any { it.type == ParameterType.INTEGER || it.type == ParameterType.NUMBER }
        OutputType.BOOLEAN -> next.parameters.any { it.type == ParameterType.BOOLEAN }
        OutputType.OBJECT -> next.parameters.any { it.type == ParameterType.STRING }
        OutputType.NONE -> false
    }
}

private fun buildWorkflow(
    steps: List<FunctionInfo>,
    isPipeline: Boolean,
    title: String,
    description: String?,
): Workflow {
    val workflowSteps = steps.mapIndexed { i, fn ->
        if (isPipeline && i > 0) {
            val prev = steps[i - 1]
            val pipelineTarget = findMatchingParam(prev.outputType, fn.parameters)
            val pipeline = if (pipelineTarget != null) {
                mapOf(pipelineTarget to "{0}")
            } else {
                emptyMap()
            }
            WorkflowStep(id = i, function = fn.name, pipeline = pipeline)
        } else if (i == 0) {
            WorkflowStep(id = i, function = fn.name)
        } else {
            WorkflowStep(id = i, function = fn.name)
        }
    }
    return Workflow(steps = workflowSteps, summary = title)
}

private fun findMatchingParam(output: OutputType, params: List<com.zhoulesin.zutils.engine.core.Parameter>): String? {
    for (param in params) {
        val match = when (output) {
            OutputType.TEXT -> param.type == ParameterType.STRING
            OutputType.NUMBER -> param.type == ParameterType.INTEGER || param.type == ParameterType.NUMBER
            OutputType.BOOLEAN -> param.type == ParameterType.BOOLEAN
            OutputType.OBJECT -> param.type == ParameterType.STRING
            else -> false
        }
        if (match) return param.name
    }
    return null
}

@Composable
private fun FunctionPickerItem(info: FunctionInfo, onClick: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(info.name, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary)
                Text(info.description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Text(
                text = info.parameters.filter { it.required }.joinToString(", ") { "${it.name}: ${it.type}" },
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
