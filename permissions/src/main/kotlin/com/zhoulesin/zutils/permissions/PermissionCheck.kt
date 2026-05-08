package com.zhoulesin.zutils.permissions

sealed interface PermissionCheck {
    data object OK : PermissionCheck
    data class NotDeclared(val permission: String) : PermissionCheck
    data class NotGranted(val permission: String) : PermissionCheck
}
