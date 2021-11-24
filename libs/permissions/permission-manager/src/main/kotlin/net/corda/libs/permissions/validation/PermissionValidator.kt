package net.corda.libs.permissions.validation

import net.corda.lifecycle.Lifecycle

interface PermissionValidator : Lifecycle {
    fun authorizeUser(requestId: String, loginName: String, permission: String): Boolean
}