package net.corda.libs.permissions.storage.reader.impl.summary

import net.corda.libs.permissions.storage.common.converter.toAvroPermissionSummary
import net.corda.data.permissions.summary.UserPermissionSummary as AvroUserPermissionSummary
import net.corda.libs.permissions.storage.reader.repository.UserLogin
import net.corda.libs.permissions.storage.reader.summary.InternalUserPermissionSummary
import net.corda.libs.permissions.storage.reader.summary.PermissionSummaryReconciler

class PermissionSummaryReconcilerImpl : PermissionSummaryReconciler {

    override fun getSummariesForReconciliation(
        dbPermissionSummaries: Map<UserLogin, InternalUserPermissionSummary>,
        cachedPermissionSummaries: Map<UserLogin, AvroUserPermissionSummary>
    ): Map<UserLogin, AvroUserPermissionSummary?> {

        val usersForReconciliation = mutableMapOf<UserLogin, AvroUserPermissionSummary?>()

        for ((currentUserLogin: UserLogin, permissionSummaryDb: InternalUserPermissionSummary) in dbPermissionSummaries) {

            val permissionSummaryCached: AvroUserPermissionSummary? = cachedPermissionSummaries[currentUserLogin]

            if (permissionSummaryCached == null) {
                // This is a new user who exists in database but not in cache. They will be added to the cache.
                usersForReconciliation[currentUserLogin] = permissionSummaryDb.toAvroUserPermissionSummary()
                continue
            }

            val thisUpdateTimestamp = permissionSummaryDb.lastUpdateTimestamp
            val lastUpdateTimestamp = permissionSummaryCached.lastUpdateTimestamp

            if (lastUpdateTimestamp > thisUpdateTimestamp) {
                // The user was updated in the cache more recently than this attempted update. E.g. another worker has made a concurrent
                // change that also affects this user. We will skip this user.
                continue
            }

            permissionSummaryDb.reconcileCachedPermissionSummary(permissionSummaryCached)
                ?.let { usersForReconciliation[currentUserLogin] = it }
        }

        reconcileForRemovedUsers(dbPermissionSummaries.keys, cachedPermissionSummaries.keys).map {
            usersForReconciliation[it] = null
        }

        return usersForReconciliation
    }

    /**
     * Returns null if there is no difference therefore no need to reconcile the user's permission summary.
     */
    private fun InternalUserPermissionSummary.reconcileCachedPermissionSummary(summaryCache: AvroUserPermissionSummary):
            AvroUserPermissionSummary? {

        val permissionSummaryDb = this
        val permissionsCache = summaryCache.permissions
        val permissionsDb = permissionSummaryDb.permissions

        if (permissionsDb.size != permissionsCache.size)
            return permissionSummaryDb.toAvroUserPermissionSummary()

        // we want ordering of permissions to be preserved
        for (i in permissionsDb.indices) {

            if (permissionsDb[i].permissionString != permissionsCache[i].permissionString)
                return permissionSummaryDb.toAvroUserPermissionSummary()

            if (permissionsDb[i].permissionType.name != permissionsCache[i].permissionType.name)
                return permissionSummaryDb.toAvroUserPermissionSummary()

            if (permissionsDb[i].groupVisibility != permissionsCache[i].groupVisibility)
                return permissionSummaryDb.toAvroUserPermissionSummary()

            if (permissionsDb[i].virtualNode != permissionsCache[i].virtualNode)
                return permissionSummaryDb.toAvroUserPermissionSummary()
        }

        // permission reconciliation is not necessary for this user
        return null
    }

    private fun reconcileForRemovedUsers(dbPermissionSummaries: Set<UserLogin>, cachedPermissionSummaries: Set<UserLogin>):
            List<UserLogin> {
        return cachedPermissionSummaries.filterNot { dbPermissionSummaries.contains(it) }
    }

    private fun InternalUserPermissionSummary.toAvroUserPermissionSummary() = AvroUserPermissionSummary(
        loginName,
        permissions.map { it.toAvroPermissionSummary() },
        lastUpdateTimestamp
    )
}

