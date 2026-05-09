package com.zhoulesin.zutils.mcp

import com.zhoulesin.zutils.engine.core.ExecutionContext
import com.zhoulesin.zutils.engine.core.FunctionInfo
import com.zhoulesin.zutils.engine.core.FunctionSource
import com.zhoulesin.zutils.engine.core.ZResult
import com.zhoulesin.zutils.engine.core.ZFunction
import kotlinx.serialization.json.JsonObject

class McpFunction(
    private val toolName: String,
    private val toolDescription: String,
    private val mcpClient: McpClient,
) : ZFunction {
    override val info = FunctionInfo(
        name = toolName,
        description = toolDescription,
        source = FunctionSource.REMOTE,
    )

    override suspend fun execute(context: ExecutionContext, args: JsonObject): ZResult {
        return try {
            val output = mcpClient.callTool(toolName, args)
            ZResult.ok(output)
        } catch (e: Exception) {
            ZResult.fail("MCP [$toolName] 失败: ${e.message}", "MCP_ERROR")
        }
    }
}
