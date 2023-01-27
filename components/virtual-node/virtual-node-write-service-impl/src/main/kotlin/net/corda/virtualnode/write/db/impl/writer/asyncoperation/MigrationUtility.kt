package net.corda.virtualnode.write.db.impl.writer.asyncoperation

import java.util.UUID
import net.corda.libs.cpi.datamodel.CpkDbChangeLogEntity
import net.corda.virtualnode.ShortHash

/**
 * Utility for running migrations.
 */
internal interface MigrationUtility {
    fun runCpiMigrations(
        virtualNodeShortHash: ShortHash,
        migrationsByCpkFileChecksum: Map<String, List<CpkDbChangeLogEntity>>,
        vaultDdlConnectionId: UUID
    )
}