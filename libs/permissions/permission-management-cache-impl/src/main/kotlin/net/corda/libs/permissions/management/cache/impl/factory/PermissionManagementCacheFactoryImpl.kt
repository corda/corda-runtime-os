package net.corda.libs.permissions.management.cache.impl.factory

import java.util.concurrent.ConcurrentHashMap
import net.corda.data.permissions.Group
import net.corda.data.permissions.Permission
import net.corda.data.permissions.Role
import net.corda.data.permissions.User
import net.corda.libs.permissions.management.cache.PermissionManagementCache
import net.corda.libs.permissions.management.cache.factory.PermissionManagementCacheFactory
import net.corda.libs.permissions.management.cache.impl.PermissionManagementCacheImpl
import org.osgi.service.component.annotations.Component

@Component(service = [PermissionManagementCacheFactory::class])
class PermissionManagementCacheFactoryImpl : PermissionManagementCacheFactory {

    override fun createPermissionManagementCache(
        userData: ConcurrentHashMap<String, User>,
        groupData: ConcurrentHashMap<String, Group>,
        roleData: ConcurrentHashMap<String, Role>,
        permissionData: ConcurrentHashMap<String, Permission>
    ): PermissionManagementCache {
        return PermissionManagementCacheImpl(userData, groupData, roleData, permissionData)
    }
}