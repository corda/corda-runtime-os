package net.corda.libs.permissions.storage.reader.impl.summary

import net.corda.libs.permissions.storage.common.converter.toAvroPermissionSummary
import net.corda.libs.permissions.storage.reader.repository.UserLogin
import net.corda.libs.permissions.storage.reader.summary.InternalUserPermissionSummary
import net.corda.libs.permissions.storage.reader.summary.PermissionSummaryReconciler
import net.corda.v5.base.util.debug
import org.slf4j.LoggerFactory
import net.corda.data.permissions.summary.UserPermissionSummary as AvroUserPermissionSummary

class PermissionSummaryReconcilerImpl : PermissionSummaryReconciler {

    private companion object {
        val logger = LoggerFactory.getLogger(this::class.java.enclosingClass)
    }

    override fun getSummariesForReconciliation(
        dbPermissionSummaries: Map<UserLogin, InternalUserPermissionSummary>,
        cachedPermissionSummaries: Map<UserLogin, AvroUserPermissionSummary>
    ): Map<UserLogin, AvroUserPermissionSummary?> {

        val usersForReconciliation = mutableMapOf<UserLogin, AvroUserPermissionSummary?>()

        for ((currentUserLogin: UserLogin, permissionSummaryDb: InternalUserPermissionSummary) in dbPermissionSummaries) {

            val permissionSummaryCached: AvroUserPermissionSummary? = cachedPermissionSummaries[currentUserLogin]

            if (permissionSummaryCached == null) {
                logger.debug { "Permission summary reconciliation task discovered new user $currentUserLogin." }
                usersForReconciliation[currentUserLogin] = permissionSummaryDb.toAvroUserPermissionSummary()
                continue
            }

            val thisUpdateTimestamp = permissionSummaryDb.lastUpdateTimestamp
            val lastUpdateTimestamp = permissionSummaryCached.lastUpdateTimestamp

            if (lastUpdateTimestamp > thisUpdateTimestamp) {
                // This could happen if the same user was affected by a change from another concurrent reconciliation task.
                // We will not overwrite a more recently calculated permission summary.
                logger.debug {
                    "Permission summary reconciliation task discovered a more recent version of permission summary in cache for " +
                            "user $currentUserLogin and will skip reconciliation for this user."
                }
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

        if (permissionSummaryDb.enabled != summaryCache.enabled)
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

            if (permissionsDb[i].id != permissionsCache[i].id)
                return permissionSummaryDb.toAvroUserPermissionSummary()
        }

        // permission reconciliation is not necessary for this user
        return null
    }

    private fun reconcileForRemovedUsers(dbPermissionSummaries: Set<UserLogin>, cachedPermissionSummaries: Set<UserLogin>):
            Set<UserLogin> {
        val removedUserLogins = cachedPermissionSummaries.filterNotTo(mutableSetOf()) { dbPermissionSummaries.contains(it) }
        if (removedUserLogins.isNotEmpty()) {
            logger.debug { "Permission summary reconciliation task discovered ${removedUserLogins.size} removed users." }
        }
        return removedUserLogins
    }

    private fun InternalUserPermissionSummary.toAvroUserPermissionSummary() = AvroUserPermissionSummary(
        loginName,
        enabled,
        permissions.map { it.toAvroPermissionSummary() },
        lastUpdateTimestamp
    )
}

