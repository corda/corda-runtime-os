package net.corda.libs.permissions.manager.impl.factory

import java.security.SecureRandom
import net.corda.data.permissions.management.PermissionManagementRequest
import net.corda.data.permissions.management.PermissionManagementResponse
import net.corda.libs.permissions.cache.PermissionCache
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

@Component(service = [PermissionManagerFactory::class])
class PermissionManagerFactoryImpl @Activate constructor(
    @Reference(service = PasswordServiceFactory::class)
    private val passwordServiceFactory: PasswordServiceFactory
) : PermissionManagerFactory {
    override fun create(
        rpcSender: RPCSender<PermissionManagementRequest, PermissionManagementResponse>,
        permissionCache: PermissionCache
    ): PermissionManager {
        return PermissionManagerImpl(
            PermissionUserManagerImpl(rpcSender, permissionCache, passwordServiceFactory.createPasswordService(SecureRandom())),
            PermissionGroupManagerImpl(rpcSender, permissionCache),
            PermissionRoleManagerImpl(rpcSender, permissionCache)
        )
    }
}