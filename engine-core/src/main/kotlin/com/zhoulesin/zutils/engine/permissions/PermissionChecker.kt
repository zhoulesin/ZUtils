package com.zhoulesin.zutils.engine.permissions

interface PermissionChecker {
    fun declaredButNotGranted(permissions: List<String>): List<String>
    fun notDeclaredInManifest(permissions: List<String>): List<String>
}
