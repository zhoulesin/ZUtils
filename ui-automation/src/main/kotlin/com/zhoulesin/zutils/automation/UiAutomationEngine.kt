package com.zhoulesin.zutils.automation

import android.util.Log

data class UiStep(
    val action: String,
    val target: String = "",
    val value: String? = null,
) {
    companion object {
        const val OPEN_APP = "open_app"
        const val CLICK = "click"
        const val INPUT = "input"
        const val SCROLL = "scroll"
        const val READ_SCREEN = "read_screen"
        const val GO_BACK = "go_back"
        const val GO_HOME = "go_home"
        const val SEARCH = "search"
        const val TAP_SEND = "tap_send"
        const val TAP_ATTACH = "tap_attach"
        const val TAP_SEARCH = "tap_search"
    }
}

sealed class UiResult {
    data class Success(val message: String) : UiResult()
    data class Failure(val step: Int, val reason: String) : UiResult()
}

/**
 * 无障碍指令执行器。按顺序执行 UiStep 列表。
 */
class UiAutomationEngine {

    companion object {
        private const val TAG = "ZUtils-UI-Engine"
    }

    fun execute(steps: List<UiStep>): UiResult {
        val service = UiAutomationService.instance
            ?: return UiResult.Failure(-1, "无障碍服务未开启，请到 设置→无障碍 中开启 ZOffice")

        for ((i, step) in steps.withIndex()) {
            Thread.sleep(400)
            val ok = runCatching { executeStep(service, step) }.getOrDefault(false)
            if (!ok) {
                Log.w(TAG, "Step $i failed: ${step.action} ${step.target}")
                return UiResult.Failure(i, "步骤 $i 执行失败: ${step.action} '${step.target}'")
            }
            Log.i(TAG, "Step $i OK: ${step.action} '${step.target}'")
        }
        return UiResult.Success("全部步骤执行完成 (${steps.size} 步)")
    }

    private fun executeStep(service: UiAutomationService, step: UiStep): Boolean = when (step.action) {
        UiStep.GO_HOME -> service.goHome()
        UiStep.GO_BACK -> service.goBack()
        UiStep.OPEN_APP -> service.openApp(step.target)
        UiStep.CLICK -> service.clickByText(step.target)
        UiStep.INPUT -> service.inputText(step.target, step.value ?: "")
        UiStep.SCROLL -> service.scrollForward()
        UiStep.READ_SCREEN -> true // 由上层处理结果
        UiStep.SEARCH -> {
            service.clickByContentDesc(step.target)
        }
        UiStep.TAP_SEND -> {
            // 微信/钉钉发送按钮
            service.clickByContentDesc("发送") || service.clickByText("发送")
        }
        UiStep.TAP_ATTACH -> {
            // 附件按钮
            service.clickByContentDesc("更多") || service.clickByContentDesc("附件") ||
                service.clickByText("+")
        }
        UiStep.TAP_SEARCH -> {
            // 搜索按钮
            service.clickByContentDesc("搜索") || service.clickByContentDesc("search")
        }
        else -> {
            Log.w(TAG, "Unknown action: ${step.action}")
            false
        }
    }

    /**
     * MVP 预设：三个硬编码指令，用于冷启动验证。
     */

    // 指令 1：打开微信 → 搜索联系人 → 发送消息
    fun sendChatMessage(targetName: String, message: String): UiResult {
        return execute(listOf(
            UiStep(UiStep.OPEN_APP, "com.tencent.mm"),
            UiStep(UiStep.TAP_SEARCH),
            UiStep(UiStep.INPUT, "搜索", targetName),
            UiStep(UiStep.CLICK, targetName),
            UiStep(UiStep.INPUT, "输入", message),
            UiStep(UiStep.TAP_SEND),
        ))
    }

    // 指令 2：打开通讯录 → 搜索联系人 → 读号码
    fun findContact(name: String): String {
        val service = UiAutomationService.instance ?: return "无障碍服务未开启"
        runCatching {
            service.openApp("com.android.contacts")
            service.clickByText("搜索")
            service.inputText("搜索", name)
            Thread.sleep(800)
            return service.getScreenText()
        }
        return "查询失败"
    }

    // 指令 3：拉下通知栏 → 读最新通知
    fun readNotifications(): List<String> {
        val service = UiAutomationService.instance ?: return emptyList()
        service.performGlobalAction(android.accessibilityservice.AccessibilityService.GLOBAL_ACTION_NOTIFICATIONS)
        Thread.sleep(600)
        val text = service.getScreenText()
        return text.lines().filter { it.isNotBlank() }.take(10)
    }
}
