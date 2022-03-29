package net.corda.libs.permissions.validation.cache.impl

import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean
import net.corda.data.permissions.summary.UserPermissionSummary
import net.corda.libs.permissions.validation.cache.PermissionValidationCache
import net.corda.libs.permissions.cache.exception.PermissionCacheException

/**
 * The Permission cache holds the data used in the RBAC permission system.
 */
internal class PermissionValidationCacheImpl(
    _permissionSummaryData: ConcurrentHashMap<String, UserPermissionSummary>
) : PermissionValidationCache {

    override val permissionSummaries: ConcurrentHashMap<String, UserPermissionSummary> = _permissionSummaryData
        get() {
            validateCacheIsRunning()
            return field
        }

    private var running = AtomicBoolean(false)

    override val isRunning: Boolean
        get() = running.get()

    override fun getPermissionSummary(loginName: String): UserPermissionSummary? {
        validateCacheIsRunning()
        return permissionSummaries[loginName.toLowerCase()]
    }

    private fun validateCacheIsRunning() {
        if (!isRunning) {
            throw PermissionCacheException("Permission validation cache is not running.")
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