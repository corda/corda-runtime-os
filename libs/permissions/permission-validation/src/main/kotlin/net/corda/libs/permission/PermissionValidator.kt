package net.corda.libs.permission

import net.corda.lifecycle.Lifecycle

interface PermissionValidator : Lifecycle {
    fun authorizeUser(requestId: String, loginName: String, permission: String): Boolean
}