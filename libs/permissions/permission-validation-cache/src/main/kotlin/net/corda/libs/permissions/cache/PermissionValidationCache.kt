package net.corda.libs.permissions.cache

import net.corda.data.permissions.summary.UserPermissionSummary
import net.corda.lifecycle.Lifecycle

/**
 * This interface defines a permission cache capable of maintaining a lifecycle and returning permission summary data.
 */
interface PermissionValidationCache : Lifecycle {
    val permissionSummaries: Map<String, UserPermissionSummary>

    fun getPermissionSummary(loginName: String): UserPermissionSummary?
}