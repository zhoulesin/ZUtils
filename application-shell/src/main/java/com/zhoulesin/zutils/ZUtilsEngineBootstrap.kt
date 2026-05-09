package com.zhoulesin.zutils

import androidx.activity.ComponentActivity
import com.zhoulesin.zutils.data.DatabaseProvider
import com.zhoulesin.zutils.data.PluginInstallRepo
import com.zhoulesin.zutils.engine.AndroidAutomationEngine
import com.zhoulesin.zutils.engine.Engine
import com.zhoulesin.zutils.functions.Base64Function
import com.zhoulesin.zutils.functions.CreateAutomationFunction
import com.zhoulesin.zutils.functions.CreateCalendarEventFunction
import com.zhoulesin.zutils.functions.GetClipboardFunction
import com.zhoulesin.zutils.functions.GetDeviceInfoFunction
import com.zhoulesin.zutils.functions.GetNetworkTypeFunction
import com.zhoulesin.zutils.functions.GetScreenInfoFunction
import com.zhoulesin.zutils.functions.GetStorageInfoFunction
import com.zhoulesin.zutils.functions.MakePhoneCallFunction
import com.zhoulesin.zutils.functions.QueryCalendarEventsFunction
import com.zhoulesin.zutils.functions.QueryContactsFunction
import com.zhoulesin.zutils.functions.ReadFileFunction
import com.zhoulesin.zutils.functions.ReadSmsFunction
import com.zhoulesin.zutils.functions.SendNotificationFunction
import com.zhoulesin.zutils.functions.SendSmsFunction
import com.zhoulesin.zutils.functions.SetClipboardFunction
import com.zhoulesin.zutils.functions.ShareFileFunction
import com.zhoulesin.zutils.functions.WriteFileFunction
//import com.zhoulesin.zutils.functions.time.GetCurrentTimeFunction
import com.zhoulesin.zutils.config.ServerConfig
import com.zhoulesin.zutils.llm.ServerLlmClient
import com.zhoulesin.zutils.mcp.McpClient
import com.zhoulesin.zutils.mcp.McpFunction
import com.zhoulesin.zutils.permissions.AndroidPermissionChecker
import com.zhoulesin.zutils.plugin.DefaultDexLoader
import com.zhoulesin.zutils.plugin.bridge.AppApiBridge
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.launch

/**
 * 构建并注册 Engine、自动化与插件缓存。供各 Application 模块（如 :app、:app-office）复用。
 */
object ZUtilsEngineBootstrap {

    fun bootstrap(activity: ComponentActivity): Engine {
        val pluginRepo = PluginInstallRepo(activity)
        val db = DatabaseProvider.get(activity)
        val autoDao = db.automationRuleDao()
        AppApiBridge.init(activity)

        val llmClient = ServerLlmClient(ServerConfig.DEFAULT_BASE_URL)

        val engine = Engine(
            androidContext = activity,
            permissionChecker = AndroidPermissionChecker(activity),
            llmClient = llmClient,
            dexLoader = DefaultDexLoader(
                activity,
                remoteBaseUrl = "https://raw.githubusercontent.com/zhoulesin/ZUtils-plugins/main",
            ),
            onPluginLoaded = { name, version, className ->
                MainScope().launch {
                    pluginRepo.save(name, version, className)
                }
            },
        )
        val autoEngine = AndroidAutomationEngine(activity, autoDao, engine.registry)
        engine.also {
//            it.registry.register(GetCurrentTimeFunction())
            it.registry.register(GetDeviceInfoFunction())
            it.registry.register(SetClipboardFunction())
            it.registry.register(GetClipboardFunction())
            it.registry.register(GetScreenInfoFunction())
            it.registry.register(GetStorageInfoFunction())
            it.registry.register(GetNetworkTypeFunction())
            it.registry.register(SendNotificationFunction())
            it.registry.register(Base64Function())
            it.registry.register(CreateAutomationFunction(autoEngine))
            it.registry.register(CreateCalendarEventFunction())
            it.registry.register(QueryCalendarEventsFunction())
            it.registry.register(QueryContactsFunction())
            it.registry.register(MakePhoneCallFunction())
            it.registry.register(SendSmsFunction())
            it.registry.register(ReadSmsFunction())
            it.registry.register(ReadFileFunction())
            it.registry.register(WriteFileFunction())
            it.registry.register(ShareFileFunction())
        }

        MainScope().launch {
            autoEngine.rescheduleAll()
        }

        MainScope().launch {
            engine.dexLoader?.let { pluginRepo.loadCachedPlugins(it, engine.registry) }
        }

        // Register MCP tools from server
        MainScope().launch {
            val mcpClient = McpClient()
            try {
                val tools = mcpClient.listTools()
                for (tool in tools) {
                    engine.registry.register(McpFunction(tool.name, tool.description, mcpClient))
                }
            } catch (_: Exception) {
                // Server not available yet, MCP functions will not be registered
            }
        }

        return engine
    }
}
