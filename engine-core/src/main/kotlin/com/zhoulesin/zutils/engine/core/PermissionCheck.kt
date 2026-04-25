package com.zhoulesin.zutils.engine.core

sealed interface PermissionCheck {
    data object OK : PermissionCheck
    data class NotDeclared(val permission: String) : PermissionCheck
    data class NotGranted(val permission: String) : PermissionCheck
}
