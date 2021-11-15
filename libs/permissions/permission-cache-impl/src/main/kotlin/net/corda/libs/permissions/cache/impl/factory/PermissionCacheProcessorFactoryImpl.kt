package net.corda.libs.permissions.cache.impl.factory

import java.util.concurrent.ConcurrentHashMap
import net.corda.data.permissions.Group
import net.corda.data.permissions.Role
import net.corda.data.permissions.User
import net.corda.libs.permissions.cache.factory.PermissionCacheProcessorFactory
import net.corda.libs.permissions.cache.impl.processor.GroupTopicProcessor
import net.corda.libs.permissions.cache.impl.processor.RoleTopicProcessor
import net.corda.libs.permissions.cache.impl.processor.UserTopicProcessor
import net.corda.libs.permissions.cache.processor.PermissionCacheGroupProcessor
import net.corda.libs.permissions.cache.processor.PermissionCacheRoleProcessor
import net.corda.libs.permissions.cache.processor.PermissionCacheUserProcessor
import net.corda.lifecycle.LifecycleCoordinator
import org.osgi.service.component.annotations.Component

@Component(service = [PermissionCacheProcessorFactory::class], immediate = true)
class PermissionCacheProcessorFactoryImpl : PermissionCacheProcessorFactory {
    override fun createUserProcessor(
        coordinator: LifecycleCoordinator,
        userData: ConcurrentHashMap<String, User>
    ): PermissionCacheUserProcessor {
        return UserTopicProcessor(coordinator, userData)
    }

    override fun createGroupProcessor(
        coordinator: LifecycleCoordinator,
        groupData: ConcurrentHashMap<String, Group>
    ): PermissionCacheGroupProcessor {
        return GroupTopicProcessor(coordinator, groupData)
    }

    override fun createRoleProcessor(
        coordinator: LifecycleCoordinator,
        roleData: ConcurrentHashMap<String, Role>
    ): PermissionCacheRoleProcessor {
        return RoleTopicProcessor(coordinator, roleData)
    }
}