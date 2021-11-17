package net.corda.libs.permissions.manager.impl.factory

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
import org.osgi.service.component.annotations.Component

@Component(service = [PermissionManagerFactory::class], immediate = true)
class PermissionManagerFactoryImpl : PermissionManagerFactory {
    override fun create(
        rpcSender: RPCSender<PermissionManagementRequest, PermissionManagementResponse>,
        permissionCache: PermissionCache
    ): PermissionManager {
        return PermissionManagerImpl(
            PermissionUserManagerImpl(rpcSender, permissionCache),
            PermissionGroupManagerImpl(rpcSender, permissionCache),
            PermissionRoleManagerImpl(rpcSender, permissionCache)
        )
    }
}