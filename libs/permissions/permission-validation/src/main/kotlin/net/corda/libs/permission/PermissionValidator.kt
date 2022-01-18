package net.corda.libs.permission

import net.corda.lifecycle.Lifecycle

interface PermissionValidator : Lifecycle {
    fun authenticateUser(loginName: String, password: CharArray): Boolean
    fun authorizeUser(loginName: String, permission: String): Boolean
}