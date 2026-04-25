package com.zhoulesin.zutils.engine

import android.content.Context
import android.util.Log
import com.zhoulesin.zutils.engine.core.ExecutionContext
import com.zhoulesin.zutils.engine.core.FunctionRegistry
import com.zhoulesin.zutils.engine.dex.DexLoader
import com.zhoulesin.zutils.engine.llm.LlmClient
import com.zhoulesin.zutils.engine.registry.DefaultFunctionRegistry
import com.zhoulesin.zutils.engine.workflow.DefaultWorkflowEngine
import com.zhoulesin.zutils.engine.workflow.Workflow
import com.zhoulesin.zutils.engine.workflow.WorkflowEngine
import com.zhoulesin.zutils.engine.workflow.WorkflowResult
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

class Engine(
    val androidContext: Context,
    val registry: FunctionRegistry = DefaultFunctionRegistry(),
    val workflowEngine: WorkflowEngine = DefaultWorkflowEngine(),
    val dexLoader: DexLoader? = null,
    val llmClient: LlmClient? = null,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    suspend fun execute(
        workflow: Workflow,
    ): WorkflowResult {
        val dexLog = mutableListOf<String>()
        resolveMissingFunctions(workflow, dexLog)
        val context = ExecutionContext(
            androidContext = androidContext,
            scope = scope,
            registry = registry,
        )
        val result = workflowEngine.execute(workflow, context)
        return if (dexLog.isNotEmpty()) {
            result.copy(dexLoadLog = dexLog)
        } else {
            result
        }
    }

    private suspend fun resolveMissingFunctions(workflow: Workflow, log: MutableList<String>) {
        val loader = dexLoader ?: return
        for (step in workflow.steps) {
            if (registry.contains(step.function)) continue
            log.add("🔍 '${step.function}' not in registry, trying DEX loader...")
            val spec = loader.resolve(step.function)
            if (spec == null) {
                log.add("❌ No DEX found for '${step.function}'")
                continue
            }
            log.add("📦 Resolved → ${spec.dexUrl} (v${spec.version})")
            for (dep in spec.dependencies) {
                log.add("   └─ ${dep.name} → ${dep.dexUrl} (v${dep.version})")
            }
            log.add("⬇️ Loading from assets...")
            val bytes = try {
                loader.download(spec)
            } catch (e: Exception) {
                Log.e("ZUtils-DEX", "download failed for ${spec.dexUrl}", e)
                log.add("❌ Download failed: ${e.message}")
                continue
            }
            log.add("📦 DEX size: ${bytes.size / 1024}KB, class: ${spec.className}")
            val functions = try {
                loader.load(bytes, spec)
            } catch (e: Exception) {
                Log.e("ZUtils-DEX", "load failed for ${spec.className}", e)
                log.add("❌ Load failed: ${e::class.simpleName}: ${e.message}")
                var cause = e.cause
                var depth = 0
                while (cause != null && depth < 3) {
                    log.add("   Caused by: ${cause::class.simpleName}: ${cause.message}")
                    for (ste in cause.stackTrace.take(3)) {
                        log.add("      at ${ste.className}.${ste.methodName}(${ste.fileName}:${ste.lineNumber})")
                    }
                    cause = cause.cause
                    depth++
                }
                for ((i, d) in spec.dependencies.withIndex()) {
                    log.add("   dep[$i]: ${d.name} v${d.version} → ${d.dexUrl}")
                }
                continue
            }
            functions.forEach { registered ->
                for ((depName, expectedVer) in registered.requiredDependencies) {
                    val provided = spec.dependencies.find { it.name == depName }
                    if (provided == null) {
                        log.add("⚠️ Dep '$depName' required by plugin but missing in manifest")
                    } else if (provided.version != expectedVer) {
                        log.add("⚠️ Dep '$depName' version mismatch: plugin expects $expectedVer, manifest has ${provided.version}")
                    }
                }
                registry.register(registered)
                log.add("✅ Registered '${registered.info.name}' v${spec.version} from DEX")
            }
        }
    }

    suspend fun executeWithLlm(
        userInput: String,
    ): WorkflowResult {
        val client = llmClient
            ?: throw IllegalStateException("LlmClient not configured")
        val workflow = client.parseIntent(userInput, registry.getAllInfos())
        return execute(workflow)
    }
}
