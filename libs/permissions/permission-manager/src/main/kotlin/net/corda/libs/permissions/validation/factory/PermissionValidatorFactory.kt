package net.corda.libs.permissions.validation.factory

import net.corda.libs.permissions.validation.PermissionValidator

interface PermissionValidatorFactory {

    /**
     * Create an instance of the [PermissionValidator]
     */
    fun createPermissionValidator(): PermissionValidator
}