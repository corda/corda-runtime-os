package net.corda.libs.permissions.validation.cache.factory

import java.util.concurrent.ConcurrentHashMap
import net.corda.data.permissions.summary.UserPermissionSummary
import net.corda.libs.permissions.validation.cache.PermissionValidationCache

/**
 * Factory for creating permission validation cache implementations.
 */
interface PermissionValidationCacheFactory {

    /**
     * @param permissionSummaryData reference to the map to be used to hold permission summary data
     */
    fun createPermissionValidationCache(
        permissionSummaryData: ConcurrentHashMap<String, UserPermissionSummary>
    ): PermissionValidationCache
}