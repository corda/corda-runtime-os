package net.corda.libs.cpi.datamodel.repository.impl

import java.time.Instant
import java.util.stream.Stream
import javax.persistence.EntityManager
import javax.persistence.LockModeType
import net.corda.crypto.core.parseSecureHash
import net.corda.libs.cpi.datamodel.entities.internal.CpiCpkEntity
import net.corda.libs.cpi.datamodel.entities.internal.CpiCpkKey
import net.corda.libs.cpi.datamodel.entities.internal.CpiMetadataEntity
import net.corda.libs.cpi.datamodel.entities.internal.CpiMetadataEntityKey
import net.corda.libs.cpi.datamodel.entities.internal.CpkMetadataEntity
import net.corda.libs.cpi.datamodel.repository.CpiMetadataRepository
import net.corda.libs.packaging.Cpk
import net.corda.libs.packaging.core.CpiIdentifier
import net.corda.libs.packaging.core.CpiMetadata
import net.corda.libs.packaging.core.CpkMetadata
import net.corda.v5.crypto.SecureHash

internal class CpiMetadataRepositoryImpl: CpiMetadataRepository {
    /**
     * @return null if not found
     */
    override fun findById(em: EntityManager, cpiId: CpiIdentifier): CpiMetadata? {
        return em.find(CpiMetadataEntity::class.java, cpiId.toEntity())?.toDto()
    }

    /**
     * @return null if not found
     */
    override fun findById(em: EntityManager, cpiId: CpiIdentifier, lockMode: LockModeType): CpiMetadata? {
        return em.find(CpiMetadataEntity::class.java, cpiId.toEntity(), lockMode)?.toDto()
    }

    override fun exist(em: EntityManager, cpiId: CpiIdentifier, lockMode: LockModeType): Boolean {
        return findById(em, cpiId, lockMode) != null
    }

    override fun findByNameAndSignerSummaryHash(em: EntityManager, name: String, signerSummaryHash: SecureHash): List<CpiMetadata> {
        return em.createQuery(
            "FROM ${CpiMetadataEntity::class.simpleName} c " +
                    "WHERE c.name = :cpiName " +
                    "AND c.signerSummaryHash = :cpiSignerSummaryHash",
            CpiMetadataEntity::class.java
        )
            .setParameter("cpiName", name)
            .setParameter("cpiSignerSummaryHash", signerSummaryHash.toString())
            .resultList.map { it.toDto() }
    }

    override fun findByNameAndVersion(em: EntityManager, name: String, version: String): CpiMetadata {
        return  em.createQuery(
            "SELECT cpi FROM ${CpiMetadataEntity::class.simpleName} cpi " +
                    "WHERE cpi.name = :cpiName "+
                    "AND cpi.version = :cpiVersion ",
            CpiMetadataEntity::class.java
        )
            .setParameter("cpiName", name)
            .setParameter("cpiVersion", version)
            .singleResult.toDto()
    }

    override fun findByFileChecksum(em: EntityManager, cpiFileChecksum: String): CpiMetadata? {
        val foundCpi = em.createQuery(
            "SELECT cpi FROM ${CpiMetadataEntity::class.simpleName} cpi " +
                    "WHERE upper(cpi.fileChecksum) like :cpiFileChecksum ",
            CpiMetadataEntity::class.java
        )
            .setParameter("cpiFileChecksum", "%${cpiFileChecksum.uppercase()}%")
            .resultList

        return if (foundCpi.isNotEmpty()) foundCpi[0].toDto() else null
    }

    override fun findAll(em: EntityManager): Stream<Triple<Int, Boolean, CpiMetadata>> {
        // Joining the other tables to ensure all data is fetched eagerly
        return em.createQuery(
            "FROM ${CpiMetadataEntity::class.simpleName} cpi_ " +
                    "LEFT JOIN FETCH cpi_.cpks cpk_ " +
                    "LEFT JOIN FETCH cpk_.metadata cpk_meta_ " +
                    "ORDER BY cpi_.name, cpi_.version, cpi_.signerSummaryHash",
            CpiMetadataEntity::class.java
        ).resultList.map { cpiMetadataEntity ->
           Triple(cpiMetadataEntity.entityVersion, cpiMetadataEntity.isDeleted, cpiMetadataEntity.toDto())
        }.stream()
    }

    override fun put(
        em: EntityManager,
        cpiId: CpiIdentifier,
        cpiFileName: String,
        fileChecksum: SecureHash,
        groupId: String,
        groupPolicy: String,
        fileUploadRequestId: String,
        cpks: Collection<Cpk>
    ) {
        em.persist(
            CpiMetadataEntity(
                cpiId.name,
                cpiId.version,
                cpiId.signerSummaryHash.toString(),
                cpiFileName,
                fileChecksum.toString(),
                groupPolicy,
                groupId,
                fileUploadRequestId,
                createCpiCpkRelationships(em, cpiId, cpks)
            )
        )
    }

    override fun update(
        em: EntityManager,
        cpiId: CpiIdentifier,
        cpiFileName: String,
        fileChecksum: SecureHash,
        groupId: String,
        groupPolicy: String,
        fileUploadRequestId: String,
        cpks: Collection<Cpk>,
        entityVersion: Int
    ): CpiMetadata {
        val managedCpiMetadataEntity = em.merge(
            CpiMetadataEntity(
                cpiId.name,
                cpiId.version,
                cpiId.signerSummaryHash.toString(),
                cpiFileName,
                fileChecksum.toString(),
                groupPolicy,
                groupId,
                fileUploadRequestId,
                createCpiCpkRelationships(em, cpiId, cpks),
                entityVersion = entityVersion
            )
        )

        return managedCpiMetadataEntity.toDto()
    }

    private fun CpiIdentifier.toEntity() =
        CpiMetadataEntityKey(name, version, signerSummaryHash.toString())

    private fun CpiMetadataEntity.toDto() =
        CpiMetadata(
            CpiIdentifier(name, version, parseSecureHash(signerSummaryHash)),
            parseSecureHash(fileChecksum),
            cpks.mapTo(linkedSetOf()) { it.metadata.toDto() },
            groupPolicy,
            version = entityVersion,
            timestamp = insertTimestamp ?: Instant.now()
        )

    private fun CpkMetadataEntity.toDto() =
        CpkMetadata.fromJsonAvro(serializedMetadata)

    private fun createCpiCpkRelationships(em: EntityManager, cpiId: CpiIdentifier, cpks: Collection<Cpk>): Set<CpiCpkEntity> {
        val foundCpks = em.createQuery(
            "FROM ${CpkMetadataEntity::class.java.simpleName} cpk " +
                    "WHERE cpk.cpkFileChecksum IN :cpkFileChecksums",
            CpkMetadataEntity::class.java
        )
            .setParameter("cpkFileChecksums", cpks.map { it.metadata.fileChecksum.toString() })
            .resultList
            .associateBy { it.cpkFileChecksum }

        val (existingCpks, newCpks) = cpks.partition { it.metadata.fileChecksum.toString() in foundCpks.keys }

        val newCpiCpkRelationships = newCpks.map { thisCpk -> // (Cpk type)
            val cpkFileChecksum = thisCpk.metadata.fileChecksum.toString()
            CpiCpkEntity(
                CpiCpkKey(
                    cpiId.name,
                    cpiId.version,
                    cpiId.signerSummaryHash.toString(),
                    cpkFileChecksum
                ),
                thisCpk.originalFileName!!,
                CpkMetadataEntity(
                    cpkFileChecksum,
                    thisCpk.metadata.cpkId.name,
                    thisCpk.metadata.cpkId.version,
                    thisCpk.metadata.cpkId.signerSummaryHash.toString(),
                    thisCpk.metadata.manifest.cpkFormatVersion.toString(),
                    thisCpk.metadata.toJsonAvro()
                )
            )
        }

        check(foundCpks.keys.size == existingCpks.toSet().size)
        check(foundCpks.keys == existingCpks.map { it.metadata.fileChecksum.toString() }.toSet())

        val relationshipsForExistingCpks = existingCpks.map { thisCpk ->
            val cpkFileChecksum = thisCpk.metadata.fileChecksum.toString()
            val cpiCpkKey = CpiCpkKey(
                cpiId.name,
                cpiId.version,
                cpiId.signerSummaryHash.toString(),
                cpkFileChecksum
            )

            em.find(CpiCpkEntity::class.java, cpiCpkKey)
                ?: CpiCpkEntity(
                    CpiCpkKey(
                        cpiId.name,
                        cpiId.version,
                        cpiId.signerSummaryHash.toString(),
                        cpkFileChecksum
                    ),
                    thisCpk.originalFileName!!,
                    foundCpks[cpkFileChecksum]!!
                )
        }

        val totalCpiCpkRelationships = newCpiCpkRelationships + relationshipsForExistingCpks

        return totalCpiCpkRelationships.toSet()
    }
}