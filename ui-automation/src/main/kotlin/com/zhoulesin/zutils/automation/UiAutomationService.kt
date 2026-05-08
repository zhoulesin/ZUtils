package com.zhoulesin.zutils.automation

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.content.Intent
import android.graphics.Path
import android.graphics.Rect
import android.os.Bundle
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo

class UiAutomationService : AccessibilityService() {

    companion object {
        private const val TAG = "ZUtils-UI-Auto"
        @Volatile var instance: UiAutomationService? = null
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
        Log.i(TAG, "Accessibility service connected")
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {}

    override fun onInterrupt() {}

    override fun onDestroy() {
        super.onDestroy()
        instance = null
    }

    fun findText(text: String): AccessibilityNodeInfo? {
        return rootInActiveWindow?.findAccessibilityNodeInfosByText(text)?.firstOrNull()
    }

    fun clickByText(text: String): Boolean {
        val nodes = rootInActiveWindow?.findAccessibilityNodeInfosByText(text) ?: return false
        val clickable = nodes.firstOrNull { it.isClickable } ?: nodes.firstOrNull()
        val clicked = clickable?.performAction(AccessibilityNodeInfo.ACTION_CLICK) == true
        nodes.forEach { it.recycle() }
        return clicked
    }

    fun clickByContentDesc(desc: String): Boolean {
        val nodes = rootInActiveWindow?.findAccessibilityNodeInfosByViewId(desc)
            ?: return false
        val clicked = nodes.firstOrNull()?.performAction(AccessibilityNodeInfo.ACTION_CLICK) == true
        nodes.forEach { it.recycle() }
        return clicked
    }

    fun inputText(target: String, value: String): Boolean {
        val inputNode = findFocusedEditable() ?: findEditableOnScreen(target)
        if (inputNode == null) {
            // Try focus by clicking first
            if (!clickByText(target)) return false
            Thread.sleep(300)
        }
        val node = findFocusedEditable() ?: return false
        val args = Bundle().apply {
            putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, value)
        }
        val result = node.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args)
        node.recycle()
        return result
    }

    fun openApp(packageName: String): Boolean {
        val intent = packageManager.getLaunchIntentForPackage(packageName)
            ?: return false
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        startActivity(intent)
        Thread.sleep(1000)
        return true
    }

    fun goHome(): Boolean {
        performGlobalAction(GLOBAL_ACTION_HOME)
        Thread.sleep(500)
        return true
    }

    fun goBack(): Boolean {
        performGlobalAction(GLOBAL_ACTION_BACK)
        Thread.sleep(300)
        return true
    }

    fun scrollForward(): Boolean {
        val node = rootInActiveWindow ?: return false
        val result = node.performAction(AccessibilityNodeInfo.ACTION_SCROLL_FORWARD)
        node.recycle()
        return result
    }

    fun scrollToText(text: String, maxScrolls: Int = 10): Boolean {
        for (i in 0 until maxScrolls) {
            if (findText(text) != null) return true
            Thread.sleep(500)
            if (!scrollForward()) break
        }
        return false
    }

    fun getScreenText(): String {
        val root = rootInActiveWindow ?: return ""
        val sb = StringBuilder()
        collectText(root, sb)
        root.recycle()
        return sb.toString()
    }

    fun tap(x: Float, y: Float): Boolean {
        val path = Path().apply { moveTo(x, y) }
        val gesture = GestureDescription.Builder()
            .addStroke(GestureDescription.StrokeDescription(path, 0, 1))
            .build()
        val lock = Object()
        var success = false
        dispatchGesture(gesture, null, null)
        Thread.sleep(200)
        return true
    }

    fun tapNode(node: AccessibilityNodeInfo): Boolean {
        val rect = Rect()
        node.getBoundsInScreen(rect)
        val cx = rect.centerX().toFloat()
        val cy = rect.centerY().toFloat()
        node.recycle()
        return tap(cx, cy)
    }

    private fun findFocusedEditable(): AccessibilityNodeInfo? {
        return rootInActiveWindow?.findFocus(AccessibilityNodeInfo.FOCUS_INPUT)
    }

    private fun findEditableOnScreen(target: String): AccessibilityNodeInfo? {
        val root = rootInActiveWindow ?: return null
        return findEditableNode(root, target)
    }

    private fun findEditableNode(node: AccessibilityNodeInfo, target: String): AccessibilityNodeInfo? {
        if (node.isEditable && node.text?.contains(target) == true) return node
        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            val result = findEditableNode(child, target)
            if (result != null) return result
            child.recycle()
        }
        return null
    }

    private fun collectText(node: AccessibilityNodeInfo, sb: StringBuilder) {
        val text = node.text?.toString()?.trim()
        if (!text.isNullOrEmpty()) sb.appendLine(text)
        for (i in 0 until node.childCount) {
            node.getChild(i)?.let { collectText(it, sb); it.recycle() }
        }
    }
}
