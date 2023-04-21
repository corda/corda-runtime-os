package net.corda.virtualnode.write.db.impl.writer.asyncoperation

import net.corda.libs.cpi.datamodel.CpkDbChangeLog
import java.util.UUID
import net.corda.crypto.core.ShortHash

/**
 * Utility for running migrations.
 */
internal interface MigrationUtility {
    /**
     * Runs migrations on the vault with the given virtual node connection information with the changesets contained in
     * [migrationChangeLogs].
     *
     * Requires a [vaultDdlConnectionId].
     *
     * If [migrationChangeLogs] is empty, no migrations will run.
     * If [migrationChangeLogs] contains migrations from CPKs that have already been executed, these migrations will not be run twice, thus
     * this API supports being given the full list of migrations from all CPKs.
     *
     * @param virtualNodeShortHash the [ShortHash] of the virtual node on which to run vault migrations
     * @param migrationChangeLogs the list of changelogs for all CPKs
     * @param vaultDdlConnectionId the connection identifier of the vault DDL connection details
     */
    fun runVaultMigrations(
        virtualNodeShortHash: ShortHash,
        migrationChangeLogs: List<CpkDbChangeLog>,
        vaultDdlConnectionId: UUID
    )

    /**
     * Checks if the [cpkChangelogs] have been successfully deployed on the virtual node's vault identified by [vaultDmlConnectionId].
     *
     * @param virtualNodeShortHash the virtual node identifier of which to perform diff function
     * @param cpkChangelogs the list of changesets to check if are deployed
     * @param vaultDmlConnectionId the DML connection ID of the vault to be compared
     *
     * @return Boolean indicating if the list of changelogs have been successfully executed on this vault schema
     */
    fun areChangesetsDeployedOnVault(
        virtualNodeShortHash: String,
        cpkChangelogs: List<CpkDbChangeLog>,
        vaultDmlConnectionId: UUID
    ): Boolean
}
