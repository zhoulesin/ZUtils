package com.zhoulesin.zutils

import androidx.activity.ComponentActivity
import com.zhoulesin.zutils.bridge.AppApiBridge
import com.zhoulesin.zutils.data.DatabaseProvider
import com.zhoulesin.zutils.data.PluginInstallRepo
import com.zhoulesin.zutils.engine.AutomationEngine
import com.zhoulesin.zutils.engine.Engine
import com.zhoulesin.zutils.engine.functions.Base64Function
import com.zhoulesin.zutils.engine.functions.CreateAutomationFunction
import com.zhoulesin.zutils.engine.functions.CreateCalendarEventFunction
import com.zhoulesin.zutils.engine.functions.GetClipboardFunction
import com.zhoulesin.zutils.engine.functions.GetDeviceInfoFunction
import com.zhoulesin.zutils.engine.functions.GetNetworkTypeFunction
import com.zhoulesin.zutils.engine.functions.GetScreenInfoFunction
import com.zhoulesin.zutils.engine.functions.GetStorageInfoFunction
import com.zhoulesin.zutils.engine.functions.MakePhoneCallFunction
import com.zhoulesin.zutils.engine.functions.QueryCalendarEventsFunction
import com.zhoulesin.zutils.engine.functions.QueryContactsFunction
import com.zhoulesin.zutils.engine.functions.ReadFileFunction
import com.zhoulesin.zutils.engine.functions.SendNotificationFunction
import com.zhoulesin.zutils.engine.functions.SendSmsFunction
import com.zhoulesin.zutils.engine.functions.SetClipboardFunction
import com.zhoulesin.zutils.engine.functions.ShareFileFunction
import com.zhoulesin.zutils.engine.functions.WriteFileFunction
//import com.zhoulesin.zutils.functions.time.GetCurrentTimeFunction
import com.zhoulesin.zutils.plugin.DefaultDexLoader
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.launch

/**
 * 构建并注册 Engine、自动化与插件缓存。供各 Application 模块（如 :app、:office-app）复用。
 */
object ZUtilsEngineBootstrap {

    fun bootstrap(activity: ComponentActivity): Engine {
        val pluginRepo = PluginInstallRepo(activity)
        val db = DatabaseProvider.get(activity)
        val autoDao = db.automationRuleDao()
        val autoEngine = AutomationEngine(activity, autoDao)
        AppApiBridge.init(activity)

        val engine = Engine(
            androidContext = activity,
            dexLoader = DefaultDexLoader(
                activity,
                remoteBaseUrl = "https://raw.githubusercontent.com/zhoulesin/ZUtils-plugins/main",
            ),
            onPluginLoaded = { name, version, className ->
                MainScope().launch {
                    pluginRepo.save(name, version, className)
                }
            },
        ).also {
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

        return engine
    }
}
