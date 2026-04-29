package com.zhoulesin.zutils.engine.workflow

import com.zhoulesin.zutils.engine.core.ExecutionContext

/**
 * WorkflowEngine 负责按顺序执行 Workflow 中的每个步骤。
 *
 * 执行逻辑：
 * 1. type=mcp → 跳过（结果已由服务器预填充）
 * 2. type=local/tool → 查 FunctionRegistry → 检查权限 → 执行 → 记录结果
 * 3. 步骤间支持 pipeline 传递数据
 * 4. 任意步骤失败则中断整个流程
 */
interface WorkflowEngine {
    suspend fun execute(
        workflow: Workflow,
        context: ExecutionContext,
    ): WorkflowResult
}
