package net.corda.libs.permissions

import net.corda.libs.permissions.manager.PermissionManager
import net.corda.libs.permissions.validation.PermissionValidator
import net.corda.lifecycle.Lifecycle

interface PermissionService : Lifecycle {
    val permissionManager: PermissionManager
    val permissionValidator: PermissionValidator
}