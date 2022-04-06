package net.corda.libs.permissions.management.cache.factory

import java.util.concurrent.ConcurrentHashMap
import net.corda.data.permissions.Group
import net.corda.data.permissions.Permission
import net.corda.data.permissions.Role
import net.corda.data.permissions.User
import net.corda.libs.permissions.management.cache.PermissionManagementCache

/**
 * Factory for creating permission management cache implementations.
 */
interface PermissionManagementCacheFactory {

    /**
     * @param userData reference to the map used to hold user data
     * @param groupData reference to the map used to hold group data
     * @param roleData reference to the map used to hold role data
     * @param permissionData reference to the map used to hold permission data
     */
    fun createPermissionManagementCache(
        userData: ConcurrentHashMap<String, User>,
        groupData: ConcurrentHashMap<String, Group>,
        roleData: ConcurrentHashMap<String, Role>,
        permissionData: ConcurrentHashMap<String, Permission>
    ): PermissionManagementCache
}