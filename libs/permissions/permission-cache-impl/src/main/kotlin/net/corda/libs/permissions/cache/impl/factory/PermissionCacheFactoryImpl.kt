package net.corda.libs.permissions.cache.impl.factory

import java.util.concurrent.ConcurrentHashMap
import net.corda.data.permissions.Group
import net.corda.data.permissions.Role
import net.corda.data.permissions.User
import net.corda.libs.permissions.cache.PermissionCache
import net.corda.libs.permissions.cache.factory.PermissionCacheFactory
import net.corda.libs.permissions.cache.impl.PermissionCacheImpl
import org.osgi.service.component.annotations.Component

@Component(service = [PermissionCacheFactory::class])
class PermissionCacheFactoryImpl : PermissionCacheFactory {
    override fun createPermissionCache(
        userData: ConcurrentHashMap<String, User>,
        groupData: ConcurrentHashMap<String, Group>,
        roleData: ConcurrentHashMap<String, Role>
    ): PermissionCache {
        return PermissionCacheImpl(userData, groupData, roleData)
    }
}