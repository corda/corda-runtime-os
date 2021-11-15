package net.corda.libs.permissions.cache.impl

import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean
import net.corda.data.permissions.Group
import net.corda.data.permissions.Role
import net.corda.data.permissions.User
import net.corda.libs.permissions.cache.PermissionCache
import net.corda.libs.permissions.cache.exception.PermissionCacheException

/**
 * The Permission cache holds the data used in the RBAC permission system.
 */
internal class PermissionCacheImpl(
    private val userData: ConcurrentHashMap<String, User>,
    private val groupData: ConcurrentHashMap<String, Group>,
    private val roleData: ConcurrentHashMap<String, Role>
) : PermissionCache {

    private var running = AtomicBoolean(false)

    override val isRunning: Boolean
        get() = running.get()

    override fun getUser(loginName: String): User? {
        validateCacheIsRunning()
        return userData[loginName]
    }

    override fun getGroup(groupId: String): Group? {
        validateCacheIsRunning()
        return groupData[groupId]
    }

    override fun getRole(roleId: String): Role? {
        validateCacheIsRunning()
        return roleData[roleId]
    }

    override fun getUsers(): Map<String, User> {
        validateCacheIsRunning()
        return userData
    }

    override fun getGroups(): Map<String, Group> {
        validateCacheIsRunning()
        return groupData
    }

    override fun getRoles(): Map<String, Role> {
        validateCacheIsRunning()
        return roleData
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