package net.corda.processors.db.internal.reconcile.db

import net.corda.libs.cpi.datamodel.findAllCpiMetadata
import net.corda.libs.packaging.core.CpiIdentifier
import net.corda.libs.packaging.core.CpiMetadata
import net.corda.libs.packaging.core.CpkMetadata
import net.corda.reconciliation.VersionedRecord
import net.corda.v5.crypto.SecureHash
import java.time.Instant
import java.util.stream.Stream

/**
 * Converts the database entity classes [CpiMetadataEntity] to [CpiMetadata], and also the identifier
 */
val getAllCpiInfoDBVersionedRecords
        : (ReconciliationContext) -> Stream<VersionedRecord<CpiIdentifier, CpiMetadata>> = { context ->
    context.getOrCreateEntityManager().findAllCpiMetadata().map { cpiMetadataEntity ->
        val cpiId = CpiIdentifier(
            cpiMetadataEntity.name,
            cpiMetadataEntity.version,
            if (cpiMetadataEntity.signerSummaryHash != "")
                SecureHash.parse(cpiMetadataEntity.signerSummaryHash)
            else
                null
        )
        object : VersionedRecord<CpiIdentifier, CpiMetadata> {
            override val version = cpiMetadataEntity.entityVersion
            override val isDeleted = cpiMetadataEntity.isDeleted
            override val key = cpiId
            override val value by lazy {
                CpiMetadata(
                    cpiId = cpiId,
                    fileChecksum = SecureHash.parse(cpiMetadataEntity.fileChecksum),
                    cpksMetadata = cpiMetadataEntity.cpks.map {
                        CpkMetadata.fromJsonAvro(it.metadata.serializedMetadata)
                    },
                    groupPolicy = cpiMetadataEntity.groupPolicy,
                    version = cpiMetadataEntity.entityVersion,
                    timestamp = cpiMetadataEntity.insertTimestamp.getOrNow()
                )
            }
        }
    }
}

private fun Instant?.getOrNow(): Instant {
    return this ?: Instant.now()
}