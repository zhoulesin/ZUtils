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

    @Volatile
    private var pendingWindowPackage: String? = null

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
        Log.i(TAG, "Accessibility service connected")
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event != null) {
            Log.i(TAG, "onAccessibilityEvent: type=${event.eventType} pkg=${event.packageName}")
            val pending = pendingWindowPackage
            if (pending != null && event.packageName?.toString() == pending) {
                pendingWindowPackage = null
                Log.i(TAG, "onAccessibilityEvent: matched pending '$pending'")
            }
        }
    }

    override fun onInterrupt() {}

    override fun onDestroy() {
        super.onDestroy()
        instance = null
    }

    fun findText(text: String): AccessibilityNodeInfo? {
        return rootInActiveWindow?.findAccessibilityNodeInfosByText(text)?.firstOrNull()
    }

    fun clickByText(text: String): Boolean {
        val allNodes = rootInActiveWindow?.findAccessibilityNodeInfosByText(text) ?: return false
        // Prefer nodes whose text property actually matches; fall back to fuzzy if none
        val exactNodes = allNodes.filter { it.text?.toString()?.contains(text) == true }
        val nodes = if (exactNodes.isNotEmpty()) exactNodes else allNodes
        Log.i(TAG, "clickByText($text): found ${nodes.size} nodes (${exactNodes.size} exact, ${allNodes.size} fuzzy)")
        nodes.forEachIndexed { i, n ->
            val r = android.graphics.Rect()
            n.getBoundsInScreen(r)
            Log.i(TAG, "  [$i] text='${n.text}' contentDesc='${n.contentDescription}' className='${n.className}' clickable=${n.isClickable} bounds=$r")
        }
        val clickable = nodes.firstOrNull { it.isClickable } ?: nodes.firstOrNull()
        val clicked = clickable?.performAction(AccessibilityNodeInfo.ACTION_CLICK) == true
        Log.i(TAG, "clickByText($text): clicked=${clicked}, chosen text='${clickable?.text}' desc='${clickable?.contentDescription}'")
        allNodes.forEach { it.recycle() }
        return clicked
    }

    fun clickByContentDesc(desc: String): Boolean {
        val allNodes = rootInActiveWindow?.findAccessibilityNodeInfosByText(desc) ?: return false
        val matched = allNodes.filter { desc.equals(it.contentDescription?.toString(), ignoreCase = true) }
        if (matched.isEmpty()) { allNodes.forEach { it.recycle() }; return false }
        val target = matched.firstOrNull { it.isClickable } ?: matched.firstOrNull()
        val clicked = target?.performAction(AccessibilityNodeInfo.ACTION_CLICK) == true
        allNodes.forEach { it.recycle() }
        return clicked
    }

    fun inputText(target: String, value: String): Boolean {
        var focused = findFocusedEditable()
        Log.i(TAG, "inputText: focused=$focused")
        if (focused == null) {
            val editable = findEditableOnScreen(target)
            Log.i(TAG, "inputText: editableOnScreen($target)=$editable")
            if (editable == null) {
                if (target.isNotEmpty()) {
                    val clicked = clickByText(target)
                    Log.i(TAG, "inputText: clickByText($target)=$clicked")
                    if (!clicked) return false
                }
                for (attempt in 1..3) {
                    Thread.sleep(500)
                    focused = findFocusedEditable()
                    Log.i(TAG, "inputText: retry $attempt, focused=$focused")
                    if (focused != null) break
                    // Also try finding any editable on screen (not just focused)
                    val anyEdit = findEditableOnScreen("")
                    if (anyEdit != null) {
                        Log.i(TAG, "inputText: found un-focused editable, clicking to focus")
                        anyEdit.performAction(AccessibilityNodeInfo.ACTION_FOCUS)
                        focused = anyEdit
                        break
                    }
                }
            }
        }
        if (focused == null) focused = findFocusedEditable()
        Log.i(TAG, "inputText: after focus, focused=$focused")
        val node = focused ?: return false
        val args = Bundle().apply {
            putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, value)
        }
        val result = node.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args)
        node.recycle()
        return result
    }

    fun openApp(packageName: String): Boolean {
        val intent = packageManager.getLaunchIntentForPackage(packageName)
        if (intent == null) {
            val pkgInfo = try {
                packageManager.getPackageInfo(packageName, 0)
            } catch (_: Exception) { null }
            Log.w(TAG, "openApp: getLaunchIntentForPackage('$packageName')=null, pkgInfo=$pkgInfo")
            return false
        }
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        startActivity(intent)

        pendingWindowPackage = packageName
        val deadline = System.currentTimeMillis() + 6000
        while (pendingWindowPackage != null && System.currentTimeMillis() < deadline) {
            Thread.sleep(100)
        }
        if (pendingWindowPackage != null) {
            Log.w(TAG, "openApp: timeout waiting for event for '$packageName'")
            pendingWindowPackage = null
            return false
        }
        Thread.sleep(500)
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
        val root = rootInActiveWindow
        if (root == null) {
            Log.i(TAG, "getScreenText: rootInActiveWindow=null")
            return ""
        }
        Log.i(TAG, "getScreenText: root pkg=${root.packageName} className=${root.className} childCount=${root.childCount}")
        val sb = StringBuilder()
        collectText(root, sb)
        root.recycle()
        val text = sb.toString()
        Log.i(TAG, "getScreenText: ${text.length} chars, first 200=${text.take(200)}")
        return text
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
        if (node.isEditable) {
            Log.i(TAG, "findEditableNode: editable node text='${node.text}' hint='${node.hintText}' contentDesc='${node.contentDescription}'")
            val text = node.text?.toString() ?: ""
            val hint = node.hintText?.toString() ?: ""
            if (text.contains(target) || hint.contains(target)) return node
        }
        if (node.className?.toString()?.contains("EditText") == true) {
            Log.i(TAG, "findEditableNode: EditText found, returning it regardless of text match")
            return node
        }
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
