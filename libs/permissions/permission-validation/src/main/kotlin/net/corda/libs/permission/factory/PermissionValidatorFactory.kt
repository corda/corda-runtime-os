package net.corda.libs.permission.factory

import net.corda.libs.permissions.cache.PermissionCache
import net.corda.libs.permission.PermissionValidator

interface PermissionValidatorFactory {

    /**
     * Create an instance of the [PermissionValidator]
     */
    fun create(permissionCache: PermissionCache): PermissionValidator
}