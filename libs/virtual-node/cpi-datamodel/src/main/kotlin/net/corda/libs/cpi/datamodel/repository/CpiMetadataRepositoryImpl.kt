package net.corda.libs.cpi.datamodel.repository

import net.corda.libs.cpi.datamodel.CpiCpk
import net.corda.libs.cpi.datamodel.entities.CpiMetadataEntity
import net.corda.libs.cpi.datamodel.entities.CpiMetadataEntityKey
import net.corda.libs.packaging.core.CpiIdentifier
import net.corda.libs.packaging.core.CpiMetadata
import net.corda.libs.packaging.core.CpkMetadata
import net.corda.v5.crypto.SecureHash
import java.time.Instant
import java.util.stream.Stream
import javax.persistence.EntityManager
import javax.persistence.LockModeType

class CpiMetadataRepositoryImpl : CpiMetadataRepository {
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
     *  Get the metadata for a given CPI
     *
     *  @return null if not found
     */
    override fun findById(em: EntityManager, id: CpiIdentifier, lockModeType: LockModeType): CpiMetadata? {
        return em.find(
            CpiMetadataEntity::class.java,
            CpiMetadataEntityKey(id.name, id.version, id.signerSummaryHash.toString()),
            lockModeType
        ).toDto()
    }

    override fun findByNameAndCpiSignerSummaryHash(
        em: EntityManager,
        cpiName: String,
        cpiSignerSummaryHash: String
    ): List<CpiMetadata> {
        return em.createQuery(
            "FROM ${CpiMetadataEntity::class.simpleName} c " +
                    "WHERE c.name = :cpiName " +
                    "AND c.signerSummaryHash = :cpiSignerSummaryHash",
            CpiMetadataEntity::class.java
        )
            .setParameter("cpiName", cpiName)
            .setParameter("cpiSignerSummaryHash", cpiSignerSummaryHash)
            .resultList.map { it.toDto() }
    }

    override fun findByNameAndVersion(em: EntityManager, name: String, version: String): CpiMetadata {
        return em.createQuery(
            "SELECT cpi FROM CpiMetadataEntity cpi " +
                    "WHERE cpi.name = :cpiName " +
                    "AND cpi.version = :cpiVersion ",
            CpiMetadataEntity::class.java
        )
            .setParameter("cpiName", name)
            .setParameter("cpiVersion", version)
            .singleResult.toDto()
    }

    override fun findByChecksum(em: EntityManager, cpiFileChecksum: String): CpiMetadata? {
        val foundCpi = em.createQuery(
            "SELECT cpi FROM CpiMetadataEntity cpi " +
                    "WHERE upper(cpi.fileChecksum) like :cpiFileChecksum ",
            CpiMetadataEntity::class.java
        )
            .setParameter("cpiFileChecksum", "%${cpiFileChecksum.uppercase()}%")
            .resultList.map { it.toDto() }
        return if (foundCpi.isNotEmpty()) foundCpi[0] else null
    }

    override fun update(
        em: EntityManager,
        cpiId: CpiIdentifier,
        fileName: String,
        fileChecksum: SecureHash,
        groupPolicy: String,
        groupId: String,
        requestId: String,
        cpiCpk: Set<CpiCpk>
    ): CpiMetadata {
        val cpiMetadataEntity = CpiMetadataEntity.create(
            name = cpiId.name,
            version = cpiId.version,
            signerSummaryHash = cpiId.signerSummaryHash.toString(),
            fileName = fileName,
            fileChecksum = fileChecksum.toString(),
            groupPolicy = groupPolicy,
            groupId = groupId,
            fileUploadRequestId = requestId,
            cpks = cpiCpk.map { it.toEntity() }.toSet()
        )

        return em.merge(cpiMetadataEntity).toDto()
    }

    override fun update(
        em: EntityManager,
        cpiMetadata: CpiMetadata,
        fileName: String,
        fileChecksum: SecureHash,
        requestId: String,
        groupId: String,
        cpiCpk: Set<CpiCpk>
    ): CpiMetadata {
        val cpiMetadataEntity = CpiMetadataEntity(
            name = cpiMetadata.cpiId.name,
            version = cpiMetadata.cpiId.version,
            signerSummaryHash = cpiMetadata.cpiId.signerSummaryHash.toString(),
            fileName = fileName,
            fileChecksum = fileChecksum.toString(),
            groupPolicy = cpiMetadata.groupPolicy,
            groupId = groupId,
            fileUploadRequestId = requestId,
            cpks = cpiCpk.map { it.toEntity() }.toSet(),
            entityVersion = cpiMetadata.version,
            insertTimestamp = cpiMetadata.timestamp
        )

        return em.merge(cpiMetadataEntity).toDto()
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
            isDeleted = isDeleted,
            groupId = groupId
        )

    private fun CpiMetadataEntity.genCpiIdentifier() =
        CpiIdentifier(name, version, SecureHash.parse(signerSummaryHash))

    private fun Instant?.getOrNow(): Instant {
        return this ?: Instant.now()
    }
}