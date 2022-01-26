package net.corda.libs.permission.impl.factory

import java.security.SecureRandom
import net.corda.libs.permission.factory.PermissionValidatorFactory
import net.corda.libs.permission.impl.PermissionValidatorImpl
import net.corda.libs.permissions.cache.PermissionCache
import net.corda.permissions.password.PasswordServiceFactory
import org.osgi.service.component.annotations.Activate
import org.osgi.service.component.annotations.Component
import org.osgi.service.component.annotations.Reference

@Component(service = [PermissionValidatorFactory::class])
class PermissionValidatorFactoryImpl @Activate constructor(
    @Reference(service = PasswordServiceFactory::class)
    private val passwordServiceFactory: PasswordServiceFactory
) : PermissionValidatorFactory {

    override fun create(permissionCache: PermissionCache): PermissionValidatorImpl {
        return PermissionValidatorImpl(
            permissionCache,
            passwordServiceFactory.createPasswordService(SecureRandom())
        )
    }
}