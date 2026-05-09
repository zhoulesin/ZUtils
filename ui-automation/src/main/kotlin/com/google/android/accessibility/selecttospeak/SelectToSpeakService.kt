package com.google.android.accessibility.selecttospeak

import com.zhoulesin.zutils.automation.UiAutomationService

/**
 * 伪装成 SelectToSpeakService，让微信暴露无障碍树。
 * 微信 8.0.58+ 只对它信任的官方无障碍服务展示完整 UI 树。
 */
class SelectToSpeakService : UiAutomationService()
