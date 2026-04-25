package com.zhoulesin.zutils.engine.core

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat

class PermissionChecker(private val context: Context) {

    private val declaredPermissions: Set<String> by lazy {
        try {
            val info = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                context.packageManager.getPackageInfo(
                    context.packageName,
                    PackageManager.PackageInfoFlags.of(PackageManager.GET_PERMISSIONS.toLong())
                )
            } else {
                @Suppress("DEPRECATION")
                context.packageManager.getPackageInfo(
                    context.packageName,
                    PackageManager.GET_PERMISSIONS
                )
            }
            info.requestedPermissions?.toSet() ?: emptySet()
        } catch (_: Exception) {
            emptySet()
        }
    }

    fun check(permissions: List<String>): PermissionCheck {
        for (perm in permissions) {
            if (perm.isBlank()) continue
            if (perm !in declaredPermissions) return PermissionCheck.NotDeclared(perm)
            if (ContextCompat.checkSelfPermission(context, perm) != PackageManager.PERMISSION_GRANTED) {
                return PermissionCheck.NotGranted(perm)
            }
        }
        return PermissionCheck.OK
    }
}
