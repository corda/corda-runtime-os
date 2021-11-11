package net.corda.libs.permissions.cache.impl

import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock
import net.corda.data.permissions.Group
import net.corda.data.permissions.Role
import net.corda.data.permissions.User
import net.corda.libs.permissions.cache.PermissionCache
import net.corda.libs.permissions.cache.exception.PermissionCacheException
import net.corda.lifecycle.LifecycleCoordinator

/**
 * The Permission cache holds the data used in the RBAC permission system.
 */
class PermissionCacheImpl(
    private val userData: ConcurrentHashMap<String, User>,
    private val groupData: ConcurrentHashMap<String, Group>,
    private val roleData: ConcurrentHashMap<String, Role>
) : PermissionCache {

    @Volatile
    private var running = false
    private val lock = ReentrantLock()

    override val isRunning: Boolean
        get() = running

    override fun getUser(loginName: String): User? {
        lock.withLock {
            if (!isRunning) {
                throw PermissionCacheException("Permission cache is not running.")
            }
            return userData[loginName]
        }
    }

    override fun getGroup(groupId: String): Group? {
        lock.withLock {
            if (!isRunning) {
                throw PermissionCacheException("Permission cache is not running.")
            }
            return groupData[groupId]
        }
    }

    override fun getRole(roleId: String): Role? {
        lock.withLock {
            if (!isRunning) {
                throw PermissionCacheException("Permission cache is not running.")
            }
            return roleData[roleId]
        }
    }

    override fun getUsers(): ConcurrentHashMap<String, User> {
        lock.withLock {
            if (!isRunning) {
                throw PermissionCacheException("Permission cache is not running.")
            }
            return userData
        }
    }

    override fun getGroups(): ConcurrentHashMap<String, Group> {
        lock.withLock {
            if (!isRunning) {
                throw PermissionCacheException("Permission cache is not running.")
            }
            return groupData
        }
    }

    override fun getRoles(): ConcurrentHashMap<String, Role> {
        lock.withLock {
            if (!isRunning) {
                throw PermissionCacheException("Permission cache is not running.")
            }
            return roleData
        }
    }

    /**
     * Starting this permission cache enables it to be reachable from other components.
     *
     * The data itself must be passed into this instance via constructor.
     */
    override fun start() {
        lock.withLock {
            running = true
        }
    }

    /**
     * Stop the permission cache from being reachable from other components.
     *
     * We don't want to completely remove the cached maps.
     */
    override fun stop() {
        lock.withLock {
            running = false
        }
    }
}