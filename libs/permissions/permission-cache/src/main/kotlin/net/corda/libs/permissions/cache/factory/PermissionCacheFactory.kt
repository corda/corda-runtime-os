package net.corda.libs.permissions.cache.factory

import java.util.concurrent.ConcurrentHashMap
import net.corda.data.permissions.Group
import net.corda.data.permissions.Permission
import net.corda.data.permissions.Role
import net.corda.data.permissions.User
import net.corda.data.permissions.summary.UserPermissionSummary
import net.corda.libs.permissions.cache.PermissionCache

interface PermissionCacheFactory {
    fun createPermissionCache(
        userData: ConcurrentHashMap<String, User>,
        groupData: ConcurrentHashMap<String, Group>,
        roleData: ConcurrentHashMap<String, Role>,
        permissionData: ConcurrentHashMap<String, Permission>,
        permissionSummaryData: ConcurrentHashMap<String, UserPermissionSummary>
    ): PermissionCache
}