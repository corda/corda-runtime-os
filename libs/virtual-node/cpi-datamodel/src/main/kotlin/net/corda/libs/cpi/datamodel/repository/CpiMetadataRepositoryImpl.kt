package net.corda.libs.cpi.datamodel.repository

import net.corda.libs.cpi.datamodel.entities.CpiMetadataEntity
import net.corda.libs.packaging.core.CpiIdentifier
import net.corda.libs.packaging.core.CpiMetadata
import net.corda.libs.packaging.core.CpkMetadata
import net.corda.v5.crypto.SecureHash
import java.time.Instant
import java.util.stream.Stream
import javax.persistence.EntityManager

class CpiMetadataRepositoryImpl: CpiMetadataRepository {
    override fun findAll(em: EntityManager): Stream<CpiMetadata> {
        // Joining the other tables to ensure all data is fetched eagerly
        return em.createQuery(
            "FROM ${CpiMetadataEntity::class.simpleName} cpi_ " +
                    "INNER JOIN FETCH cpi_.cpks cpk_ " +
                    "INNER JOIN FETCH cpk_.metadata cpk_meta_ " +
                    "ORDER BY cpi_.name, cpi_.version, cpi_.signerSummaryHash",
            CpiMetadataEntity::class.java
        ).resultStream.map { it.toDto() }
    }

    /**
    * Converts an entity to a data transport object.
    */
    private fun CpiMetadataEntity.toDto() =
        CpiMetadata(
            cpiId = genCpiIdentifier(),
            fileChecksum = SecureHash.parse(fileChecksum),
            cpksMetadata = cpks.map { CpkMetadata.fromJsonAvro(it.metadata.serializedMetadata) },
            groupPolicy = groupPolicy,
            version = entityVersion,
            timestamp = insertTimestamp.getOrNow(),
            isDeleted = isDeleted
        )

    private fun CpiMetadataEntity.genCpiIdentifier() =
        CpiIdentifier(name, version, SecureHash.parse(signerSummaryHash))

    private fun Instant?.getOrNow(): Instant {
        return this ?: Instant.now()
    }
}