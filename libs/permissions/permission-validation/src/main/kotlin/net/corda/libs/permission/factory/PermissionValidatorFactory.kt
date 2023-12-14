package net.corda.libs.permission.factory

import net.corda.libs.permission.PermissionValidator
import net.corda.libs.permissions.validation.cache.PermissionValidationCache
import java.util.concurrent.atomic.AtomicReference

interface PermissionValidatorFactory {

    /**
     * Create an instance of the [PermissionValidator]
     */
    fun create(permissionValidationCacheRef: AtomicReference<PermissionValidationCache?>): PermissionValidator
}
