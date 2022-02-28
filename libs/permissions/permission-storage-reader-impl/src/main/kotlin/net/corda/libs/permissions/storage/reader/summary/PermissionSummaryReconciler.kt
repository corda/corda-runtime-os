package net.corda.libs.permissions.storage.reader.summary

import net.corda.data.permissions.summary.UserPermissionSummary as AvroUserPermissionSummary
import net.corda.libs.permissions.storage.reader.repository.UserLogin

/**
 * Reconciliation for permission summaries in a cache against permission summaries in a data storage.
 */
interface PermissionSummaryReconciler {

    /**
     * Takes a map of permission summaries retrieved from the data storage and a map of permission summaries retrieved from a cache, this
     * function will perform diff logic and calculate which users require reconciliation to the permission summary cache.
     *
     * If a user was removed from the data storage, the map will contain key = userLogin and value = null.
     *
     * @param dbPermissionSummaries permission summaries retrieved from the data storage.
     * @param cachedPermissionSummaries permission summaries retrieved from the cache.
     * @return map of all users that need reconciliation to the cache along with the [AvroUserPermissionSummary] object.
     */
    fun getSummariesForReconciliation(
        dbPermissionSummaries: Map<UserLogin, InternalUserPermissionSummary>,
        cachedPermissionSummaries: Map<UserLogin, AvroUserPermissionSummary>
    ): Map<UserLogin, AvroUserPermissionSummary?>
}