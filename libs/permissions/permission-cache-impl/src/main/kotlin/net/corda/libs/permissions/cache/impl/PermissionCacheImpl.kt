package net.corda.libs.permissions.cache.impl

import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean
import net.corda.data.permissions.Group
import net.corda.data.permissions.Permission
import net.corda.data.permissions.Role
import net.corda.data.permissions.User
import net.corda.data.permissions.summary.UserPermissionSummary
import net.corda.libs.permissions.cache.PermissionCache
import net.corda.libs.permissions.cache.exception.PermissionCacheException

/**
 * The Permission cache holds the data used in the RBAC permission system.
 */
internal class PermissionCacheImpl(
    _userData: ConcurrentHashMap<String, User>,
    _groupData: ConcurrentHashMap<String, Group>,
    _roleData: ConcurrentHashMap<String, Role>,
    _permissionsData: ConcurrentHashMap<String, Permission>,
    _permissionSummaryData: ConcurrentHashMap<String, UserPermissionSummary>
) : PermissionCache {

    override val users: ConcurrentHashMap<String, User> = _userData
        get() {
            validateCacheIsRunning()
            return field
        }
    override val groups: ConcurrentHashMap<String, Group> = _groupData
        get() {
            validateCacheIsRunning()
            return field
        }
    override val roles: ConcurrentHashMap<String, Role> = _roleData
        get() {
            validateCacheIsRunning()
            return field
        }

    override val permissions: ConcurrentHashMap<String, Permission> = _permissionsData
        get() {
            validateCacheIsRunning()
            return field
        }

    override val permissionSummaries: ConcurrentHashMap<String, UserPermissionSummary> = _permissionSummaryData
        get() {
            validateCacheIsRunning()
            return field
        }

    private var running = AtomicBoolean(false)

    override val isRunning: Boolean
        get() = running.get()

    override fun getUser(loginName: String): User? {
        validateCacheIsRunning()
        return users[loginName.toLowerCase()]
    }

    override fun getGroup(groupId: String): Group? {
        validateCacheIsRunning()
        return groups[groupId]
    }

    override fun getRole(roleId: String): Role? {
        validateCacheIsRunning()
        return roles[roleId]
    }

    override fun getPermission(permissionId: String): Permission? {
        validateCacheIsRunning()
        return permissions[permissionId]
    }

    override fun getPermissionSummary(loginName: String): UserPermissionSummary? {
        validateCacheIsRunning()
        return permissionSummaries[loginName]
    }

    private fun validateCacheIsRunning() {
        if (!isRunning) {
            throw PermissionCacheException("Permission cache is not running.")
        }
    }

    /**
     * Starting this permission cache enables it to be reachable from other components.
     *
     * The data itself must be passed into this instance via constructor.
     */
    override fun start() {
        running.compareAndSet(false, true)
    }

    /**
     * Stop the permission cache from being reachable from other components.
     *
     * We don't want to completely remove the cached maps.
     */
    override fun stop() {
        running.set(false)
    }
}