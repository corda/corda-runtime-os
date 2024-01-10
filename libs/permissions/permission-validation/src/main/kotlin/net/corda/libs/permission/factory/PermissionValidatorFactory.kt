package net.corda.libs.permission.factory

import net.corda.libs.permissions.validation.cache.PermissionValidationCache
import net.corda.libs.permission.PermissionValidator
import java.util.concurrent.atomic.AtomicReference

interface PermissionValidatorFactory {

    /**
     * Create an instance of the [PermissionValidator]
     */
    fun create(permissionValidationCacheRef: AtomicReference<PermissionValidationCache?>): PermissionValidator
}