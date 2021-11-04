package net.corda.libs.permission

import net.corda.lifecycle.Lifecycle

interface PermissionService : Lifecycle {
    fun authorizeUser(requestId: String, loginName: String, permission: String): Boolean
}