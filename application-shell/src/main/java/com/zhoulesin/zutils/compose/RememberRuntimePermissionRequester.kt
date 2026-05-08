package com.zhoulesin.zutils.compose

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.core.content.ContextCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlin.coroutines.resume

private class PermissionGateHolder {
    var continuation: kotlinx.coroutines.CancellableContinuation<Boolean>? = null
}

/**
 * 与 [com.zhoulesin.zutils.engine.Engine.execute] 的 [requestRuntimePermissions] 对接：
 * 根据 [FunctionInfo.permissions] 中未授予项，自动弹出系统授权对话框；拒绝则返回 `false`。
 */
@Composable
fun rememberRuntimePermissionRequester(): suspend (deniedPermissions: List<String>, forFunction: String) -> Boolean {
    if (LocalInspectionMode.current) {
        val previewNoop: suspend (List<String>, String) -> Boolean = { _, _ -> true }
        return remember { previewNoop }
    }
    val context = LocalContext.current
    val activity = context as? ComponentActivity
    val holder = remember { PermissionGateHolder() }
    val mutex = remember { Mutex() }
    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) { results ->
        val allGranted = results.isNotEmpty() && results.values.all { it }
        val c = holder.continuation
        holder.continuation = null
        if (c != null && c.isActive) {
            c.resume(allGranted)
        }
    }
    DisposableEffect(Unit) {
        onDispose {
            holder.continuation?.cancel()
            holder.continuation = null
        }
    }
    return remember(activity, launcher) {
        val gate: suspend (List<String>, String) -> Boolean = gate@{ denied, _ ->
            if (denied.isEmpty()) return@gate true
            if (activity == null) return@gate false
            mutex.withLock {
                withContext(Dispatchers.Main.immediate) {
                    val distinct = denied.distinct()
                    val runtimePerms = distinct.filter { it != Manifest.permission.WRITE_SETTINGS }
                    val needWriteSettings = distinct.any { it == Manifest.permission.WRITE_SETTINGS }

                    val stillMissingRuntime = runtimePerms.filter {
                        ContextCompat.checkSelfPermission(activity, it) != PackageManager.PERMISSION_GRANTED
                    }
                    if (stillMissingRuntime.isNotEmpty()) {
                        val granted = suspendCancellableCoroutine { cont ->
                            cont.invokeOnCancellation {
                                holder.continuation = null
                            }
                            holder.continuation = cont
                            launcher.launch(stillMissingRuntime.toTypedArray())
                        }
                        if (!granted) return@withContext false
                    }

                    if (needWriteSettings && !Settings.System.canWrite(activity)) {
                        activity.startActivity(
                            Intent(Settings.ACTION_MANAGE_WRITE_SETTINGS).apply {
                                data = Uri.parse("package:${activity.packageName}")
                            },
                        )
                    }
                    true
                }
            }
        }
        gate
    }
}
