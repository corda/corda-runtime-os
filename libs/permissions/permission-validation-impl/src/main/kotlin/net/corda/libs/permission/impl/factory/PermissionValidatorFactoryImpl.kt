package net.corda.libs.permission.impl.factory

import net.corda.libs.permission.factory.PermissionValidatorFactory
import net.corda.libs.permission.impl.PermissionValidatorImpl
import net.corda.libs.permissions.validation.cache.PermissionValidationCache
import org.osgi.service.component.annotations.Component
import java.util.concurrent.atomic.AtomicReference

@Component(service = [PermissionValidatorFactory::class])
class PermissionValidatorFactoryImpl : PermissionValidatorFactory {

    override fun create(permissionValidationCacheRef: AtomicReference<PermissionValidationCache?>): PermissionValidatorImpl {
        return PermissionValidatorImpl(permissionValidationCacheRef)
    }
}