package net.corda.libs.permission.impl.factory

import net.corda.libs.permission.factory.PermissionValidatorFactory
import net.corda.libs.permission.impl.PermissionValidatorImpl
import net.corda.libs.permissions.cache.PermissionCache
import org.osgi.service.component.annotations.Component

@Component(service = [PermissionValidatorFactory::class])
class PermissionValidatorFactoryImpl : PermissionValidatorFactory {

    override fun createPermissionValidator(
        permissionCache: PermissionCache
    ): PermissionValidatorImpl {
        return PermissionValidatorImpl(permissionCache)
    }
}