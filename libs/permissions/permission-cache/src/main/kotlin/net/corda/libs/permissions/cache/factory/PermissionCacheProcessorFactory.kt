package net.corda.libs.permissions.cache.factory

import java.util.concurrent.ConcurrentHashMap
import net.corda.data.permissions.Group
import net.corda.data.permissions.Role
import net.corda.data.permissions.User
import net.corda.libs.permissions.cache.processor.PermissionCacheGroupProcessor
import net.corda.libs.permissions.cache.processor.PermissionCacheRoleProcessor
import net.corda.libs.permissions.cache.processor.PermissionCacheUserProcessor
import net.corda.lifecycle.LifecycleCoordinator

interface PermissionCacheProcessorFactory {
    fun createUserProcessor(coordinator: LifecycleCoordinator, userData: ConcurrentHashMap<String, User>): PermissionCacheUserProcessor
    fun createGroupProcessor(coordinator: LifecycleCoordinator, groupData: ConcurrentHashMap<String, Group>): PermissionCacheGroupProcessor
    fun createRoleProcessor(coordinator: LifecycleCoordinator, roleData: ConcurrentHashMap<String, Role>): PermissionCacheRoleProcessor
}