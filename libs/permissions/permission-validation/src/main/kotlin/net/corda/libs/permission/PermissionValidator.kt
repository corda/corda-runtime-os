package net.corda.libs.permission

import net.corda.lifecycle.Lifecycle

interface PermissionValidator : Lifecycle {
    /**
     * @return `true` if a user is allowed to perform [operation] or `false` otherwise
     */
    fun authorizeUser(loginName: String, operation: String): Boolean
}
