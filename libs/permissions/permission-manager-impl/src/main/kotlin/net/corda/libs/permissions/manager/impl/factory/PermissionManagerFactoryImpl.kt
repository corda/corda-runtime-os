package net.corda.libs.permissions.manager.impl.factory

import net.corda.data.permissions.management.PermissionManagementRequest
import net.corda.data.permissions.management.PermissionManagementResponse
import net.corda.libs.configuration.SmartConfig
import net.corda.libs.permissions.management.cache.PermissionManagementCache
import net.corda.libs.permissions.manager.BasicAuthenticationService
import net.corda.libs.permissions.manager.PermissionManager
import net.corda.libs.permissions.manager.factory.PermissionManagerFactory
import net.corda.libs.permissions.manager.impl.PermissionEntityManagerImpl
import net.corda.libs.permissions.manager.impl.PermissionGroupManagerImpl
import net.corda.libs.permissions.manager.impl.PermissionManagerImpl
import net.corda.libs.permissions.manager.impl.PermissionRoleManagerImpl
import net.corda.libs.permissions.manager.impl.PermissionUserManagerImpl
import net.corda.libs.permissions.manager.impl.RbacBasicAuthenticationService
import net.corda.libs.permissions.validation.cache.PermissionValidationCache
import net.corda.messaging.api.publisher.RPCSender
import net.corda.permissions.password.PasswordServiceFactory
import org.osgi.service.component.annotations.Activate
import org.osgi.service.component.annotations.Component
import org.osgi.service.component.annotations.Reference
import java.security.SecureRandom
import java.util.concurrent.atomic.AtomicReference

@Component(service = [PermissionManagerFactory::class])
class PermissionManagerFactoryImpl @Activate constructor(
    @Reference(service = PasswordServiceFactory::class)
    private val passwordServiceFactory: PasswordServiceFactory
) : PermissionManagerFactory {

    override fun createPermissionManager(
        config: SmartConfig,
        rpcSender: RPCSender<PermissionManagementRequest, PermissionManagementResponse>,
        permissionManagementCacheRef: AtomicReference<PermissionManagementCache?>,
        permissionValidationCacheRef: AtomicReference<PermissionValidationCache?>
    ): PermissionManager {

        return PermissionManagerImpl(
            PermissionUserManagerImpl(
                config,
                rpcSender,
                permissionManagementCacheRef,
                permissionValidationCacheRef,
                passwordServiceFactory.createPasswordService(SecureRandom())
            ),
            PermissionGroupManagerImpl(config, rpcSender, permissionManagementCacheRef),
            PermissionRoleManagerImpl(config, rpcSender, permissionManagementCacheRef),
            PermissionEntityManagerImpl(config, rpcSender, permissionManagementCacheRef)
        )
    }

    override fun createBasicAuthenticationService(
        permissionManagementCacheRef: AtomicReference<PermissionManagementCache?>): BasicAuthenticationService {

        return RbacBasicAuthenticationService(
            permissionManagementCacheRef,
            passwordServiceFactory.createPasswordService(SecureRandom())
        )
    }
}