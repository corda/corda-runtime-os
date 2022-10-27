package net.corda.virtualnode.write.db.impl.writer.management.common

import net.corda.virtualnode.write.db.impl.writer.CpiMetadataLite
import net.corda.virtualnode.write.db.impl.writer.VirtualNodeDb

internal interface MigrationUtility{
    fun runCpiMigrations(cpiMetadata: CpiMetadataLite, vaultDb: VirtualNodeDb)
}