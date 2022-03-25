package net.corda.libs.permissions.manager.impl.factory

import java.security.SecureRandom
import net.corda.data.permissions.management.PermissionManagementRequest
import net.corda.data.permissions.management.PermissionManagementResponse
import net.corda.libs.permissions.manager.PermissionManager
import net.corda.libs.permissions.manager.factory.PermissionManagerFactory
import net.corda.libs.permissions.manager.impl.PermissionGroupManagerImpl
import net.corda.libs.permissions.manager.impl.PermissionManagerImpl
import net.corda.libs.permissions.manager.impl.PermissionRoleManagerImpl
import net.corda.libs.permissions.manager.impl.PermissionUserManagerImpl
import net.corda.messaging.api.publisher.RPCSender
import net.corda.permissions.password.PasswordServiceFactory
import org.osgi.service.component.annotations.Activate
import org.osgi.service.component.annotations.Component
import org.osgi.service.component.annotations.Reference
import net.corda.libs.configuration.SmartConfig
import net.corda.libs.permissions.cache.PermissionManagementCache
import net.corda.libs.permissions.cache.PermissionValidationCache
import net.corda.libs.permissions.manager.BasicAuthenticationService
import net.corda.libs.permissions.manager.impl.RbacBasicAuthenticationService
import net.corda.libs.permissions.manager.impl.PermissionEntityManagerImpl

@Component(service = [PermissionManagerFactory::class])
class PermissionManagerFactoryImpl @Activate constructor(
    @Reference(service = PasswordServiceFactory::class)
    private val passwordServiceFactory: PasswordServiceFactory
) : PermissionManagerFactory {

    override fun createPermissionManager(
        config: SmartConfig,
        rpcSender: RPCSender<PermissionManagementRequest, PermissionManagementResponse>,
        permissionManagementCache: PermissionManagementCache,
        permissionValidationCache: PermissionValidationCache
    ): PermissionManager {

        return PermissionManagerImpl(
            PermissionUserManagerImpl(
                config,
                rpcSender,
                permissionManagementCache,
                permissionValidationCache,
                passwordServiceFactory.createPasswordService(SecureRandom())
            ),
            PermissionGroupManagerImpl(config, rpcSender, permissionManagementCache),
            PermissionRoleManagerImpl(config, rpcSender, permissionManagementCache),
            PermissionEntityManagerImpl(config, rpcSender, permissionManagementCache)
        )
    }

    override fun createBasicAuthenticationService(permissionManagementCache: PermissionManagementCache): BasicAuthenticationService {

        return RbacBasicAuthenticationService(
            permissionManagementCache,
            passwordServiceFactory.createPasswordService(SecureRandom())
        )
    }
}