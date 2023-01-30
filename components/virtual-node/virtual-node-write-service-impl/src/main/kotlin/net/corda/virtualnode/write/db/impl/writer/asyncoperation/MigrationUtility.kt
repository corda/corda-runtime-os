package net.corda.virtualnode.write.db.impl.writer.asyncoperation

import java.util.UUID
import net.corda.libs.cpi.datamodel.CpkDbChangeLogEntity
import net.corda.virtualnode.ShortHash

/**
 * Utility for running migrations.
 */
internal interface MigrationUtility {
    /**
     * Runs migrations on the vault of the given virtual node with the changesets contained in the map of [migrationsByCpkFileChecksum].
     *
     * Requires a [vaultDdlConnectionId] to be provided.
     *
     * If [migrationsByCpkFileChecksum] is empty, no migrations will run.
     * Migrations contained in [migrationsByCpkFileChecksum] that have already been executed, will not be executed twice.
     *
     * @param virtualNodeShortHash the [ShortHash] of the virtual node on which to run vault migrations
     * @param migrationsByCpkFileChecksum the list of changelogs for each cpk file checksum
     * @param vaultDdlConnectionId the connection identifier of the vault DDL connection details
     */
    fun runVaultMigrations(
        virtualNodeShortHash: ShortHash,
        migrationsByCpkFileChecksum: List<CpkDbChangeLogEntity>,
        vaultDdlConnectionId: UUID
    )
}