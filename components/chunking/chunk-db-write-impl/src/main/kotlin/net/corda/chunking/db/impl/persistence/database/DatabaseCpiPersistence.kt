package net.corda.chunking.db.impl.persistence.database

import net.corda.chunking.RequestId
import net.corda.chunking.db.impl.persistence.CpiPersistence
import net.corda.chunking.db.impl.persistence.PersistenceUtils.signerSummaryHashForDbQuery
import net.corda.libs.cpi.datamodel.CpiCpkEntity
import net.corda.libs.cpi.datamodel.CpiCpkKey
import net.corda.libs.cpi.datamodel.CpiMetadataEntity
import net.corda.libs.cpi.datamodel.CpiMetadataEntityKey
import net.corda.libs.cpi.datamodel.CpkDbChangeLogAuditEntity
import net.corda.libs.cpi.datamodel.CpkDbChangeLogEntity
import net.corda.libs.cpi.datamodel.CpkFileEntity
import net.corda.libs.cpi.datamodel.CpkMetadataEntity
import net.corda.libs.cpiupload.DuplicateCpiUploadException
import net.corda.libs.cpiupload.ValidationException
import net.corda.libs.packaging.Cpi
import net.corda.libs.packaging.Cpk
import net.corda.orm.utils.transaction
import net.corda.v5.base.util.contextLogger
import net.corda.v5.crypto.SecureHash
import java.nio.file.Files
import java.util.UUID
import javax.persistence.EntityManager
import javax.persistence.EntityManagerFactory
import javax.persistence.LockModeType
import javax.persistence.NonUniqueResultException

/**
 * This class provides some simple APIs to interact with the database for manipulating CPIs, CPKs and their associated metadata.
 */
class DatabaseCpiPersistence(private val entityManagerFactory: EntityManagerFactory) : CpiPersistence {

    private companion object {
        val log = contextLogger()
    }

    /**
     * Check if we already have a cpk persisted with this checksum
     *
     * @return true if checksum exists in database
     */
    override fun cpkExists(cpkChecksum: SecureHash): Boolean {
        val query = "SELECT count(c) FROM ${CpkFileEntity::class.simpleName} c WHERE c.fileChecksum = :cpkFileChecksum"
        val entitiesFound = entityManagerFactory.createEntityManager().transaction {
            it.createQuery(query)
                .setParameter("cpkFileChecksum", cpkChecksum.toString())
                .singleResult as Long
        }

        if (entitiesFound > 1) throw NonUniqueResultException("CpkFileEntity with fileChecksum = $cpkChecksum was not unique")

        return entitiesFound > 0
    }

    override fun cpiExists(cpiName: String, cpiVersion: String, signerSummaryHash: String): Boolean =
        getCpiMetadataEntity(cpiName, cpiVersion, signerSummaryHash) != null

    override fun persistMetadataAndCpks(
        cpi: Cpi,
        cpiFileName: String,
        cpiFileChecksum: SecureHash,
        requestId: RequestId,
        groupId: String,
        changelogsExtractedFromCpi: List<CpkDbChangeLogEntity>
    ): CpiMetadataEntity {
        entityManagerFactory.createEntityManager().transaction { em ->

            val cpiMetadataEntity = createCpiMetadataEntity(
                cpi,
                cpiFileName,
                cpiFileChecksum,
                requestId,
                groupId,
                createCpiCpkRelationships(em, cpi)
            )

            val managedCpiMetadataEntity = em.merge(cpiMetadataEntity)

            persistNewCpkFileEntities(cpi.metadata.fileChecksum.toString(), em, cpi.cpks)

            persistNewChangelogs(changelogsExtractedFromCpi, em)

            return@persistMetadataAndCpks managedCpiMetadataEntity
        }
    }

    private fun createCpiCpkRelationships(em: EntityManager, cpi: Cpi): Set<CpiCpkEntity> {
        // there may be some CPKs that already exist. We should load these first, then create CpiCpk associations for them (if necessary).
        val foundCpks = em.createQuery(
            "FROM ${CpkMetadataEntity::class.java.simpleName} cpk " +
                    "WHERE cpk.cpkFileChecksum IN :cpkFileChecksums",
            CpkMetadataEntity::class.java
        )
            .setParameter("cpkFileChecksums", cpi.cpks.map { it.metadata.fileChecksum.toString() })
            .resultList
            .associateBy { it.cpkFileChecksum }

        val (existingCpks, newCpks) = cpi.cpks.partition { it.metadata.fileChecksum.toString() in foundCpks.keys }

        val newCpiCpkRelationships = newCpks.map { thisCpk ->
            val cpkFileChecksum = thisCpk.metadata.fileChecksum.toString()
            CpiCpkEntity(
                CpiCpkKey(
                    cpi.metadata.cpiId.name,
                    cpi.metadata.cpiId.version,
                    cpi.metadata.cpiId.signerSummaryHashForDbQuery,
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
                cpi.metadata.cpiId.name,
                cpi.metadata.cpiId.version,
                cpi.metadata.cpiId.signerSummaryHashForDbQuery,
                cpkFileChecksum
            )
            // cpiCpk relationship might already exist for this CPI, for example, if a force uploaded CPI doesn't change a CPK, otherwise
            // create a new one with the CpkMetadataEntity
            em.find(CpiCpkEntity::class.java, cpiCpkKey)
                ?: CpiCpkEntity(
                    CpiCpkKey(
                        cpi.metadata.cpiId.name,
                        cpi.metadata.cpiId.version,
                        cpi.metadata.cpiId.signerSummaryHashForDbQuery,
                        cpkFileChecksum
                    ),
                    thisCpk.originalFileName!!,
                    foundCpks[cpkFileChecksum]!!
                )
        }

        val totalCpiCpkRelationships = newCpiCpkRelationships + relationshipsForExistingCpks
        return totalCpiCpkRelationships.toSet()
    }

    /**
     * Update the changelogs in the db for cpi upload
     *
     * @property changelogsExtractedFromCpi: [List]<[CpkDbChangeLogEntity]> a list of changelogs extracted from the force
     *  uploaded cpi.
     * @property em: [EntityManager] the entity manager from the call site. We reuse this for several operations as part
     *  of CPI upload
     *
     * @return [Boolean] indicating whether we actually updated any changelogs
     */
    private fun persistNewChangelogs(
        changelogsExtractedFromCpi: List<CpkDbChangeLogEntity>,
        em: EntityManager
    ) {
        // The incoming changelogs will not be marked deleted
        changelogsExtractedFromCpi.forEach { require(!it.isDeleted) }

        changelogsExtractedFromCpi.forEach { changelogEntity ->
            log.info("Persisting changelog and audit for CPK: ${changelogEntity.id.cpkFileChecksum}, ${changelogEntity.id.filePath})")
            em.merge(changelogEntity) // merging ensures any existing changelogs have isDeleted set to false
            em.persist(
                CpkDbChangeLogAuditEntity(
                    UUID.randomUUID().toString(),
                    changelogEntity.id.cpkFileChecksum,
                    changelogEntity.id.filePath,
                    changelogEntity.content,
                    changelogEntity.isDeleted
                )
            )
        }
    }

    override fun updateMetadataAndCpks(
        cpi: Cpi,
        cpiFileName: String,
        cpiFileChecksum: SecureHash,
        requestId: RequestId,
        groupId: String,
        changelogsExtractedFromCpi: List<CpkDbChangeLogEntity>
    ): CpiMetadataEntity {
        val cpiId = cpi.metadata.cpiId
        log.info("Performing updateMetadataAndCpks for: ${cpiId.name} v${cpiId.version}")

        // Perform update of CPI and store CPK along with its metadata
        entityManagerFactory.createEntityManager().transaction { em ->
            // We cannot delete old representation of CPI as there is FK constraint from `vnode_instance`
            val existingMetadataEntity = requireNotNull(
                findCpiMetadataEntityInTransaction(
                    em,
                    cpiId.name,
                    cpiId.version,
                    cpiId.signerSummaryHashForDbQuery
                )
            ) {
                "Cannot find CPI metadata for ${cpiId.name} v${cpiId.version}"
            }

            val updatedMetadata = existingMetadataEntity.update(
                fileUploadRequestId = requestId,
                fileName = cpiFileName,
                fileChecksum = cpiFileChecksum.toString(),
                cpks = createCpiCpkRelationships(em, cpi)
            )

            val cpiMetadataEntity = em.merge(updatedMetadata)

            persistNewCpkFileEntities(cpiMetadataEntity.fileChecksum, em, cpi.cpks)

            persistNewChangelogs(changelogsExtractedFromCpi, em)

            return cpiMetadataEntity
        }
    }

    /**
     * @return null if not found
     */
    private fun findCpiMetadataEntityInTransaction(
        entityManager: EntityManager,
        name: String,
        version: String,
        signerSummaryHash: String
    ): CpiMetadataEntity? {
        val primaryKey = CpiMetadataEntityKey(
            name,
            version,
            signerSummaryHash
        )

        return entityManager.find(
            CpiMetadataEntity::class.java,
            primaryKey,
            // In case of force update, we want the entity to change regardless of whether the CPI being uploaded
            //  is identical to an existing.
            //  OPTIMISTIC_FORCE_INCREMENT means the version number will always be bumped.
            LockModeType.OPTIMISTIC_FORCE_INCREMENT
        )
    }

    private fun getCpiMetadataEntity(name: String, version: String, signerSummaryHash: String): CpiMetadataEntity? {
        return entityManagerFactory.createEntityManager().transaction {
            findCpiMetadataEntityInTransaction(it, name, version, signerSummaryHash)
        }
    }

    override fun getGroupId(cpiName: String, cpiVersion: String, signerSummaryHash: String): String? {
        return getCpiMetadataEntity(cpiName, cpiVersion, signerSummaryHash)?.groupId
    }

    /**
     * For a given CPI, create the metadata entity required to insert into the database.
     *
     * @param cpi CPI object
     * @param cpiFileName original file name
     * @param checksum checksum/hash of the CPI
     * @param requestId the requestId originating from the chunk upload
     */
    @Suppress("LongParameterList")
    private fun createCpiMetadataEntity(
        cpi: Cpi,
        cpiFileName: String,
        checksum: SecureHash,
        requestId: RequestId,
        groupId: String,
        cpiCpkEntities: Set<CpiCpkEntity>
    ): CpiMetadataEntity {
        val cpiMetadata = cpi.metadata

        return CpiMetadataEntity.create(
            name = cpiMetadata.cpiId.name,
            version = cpiMetadata.cpiId.version,
            signerSummaryHash = cpiMetadata.cpiId.signerSummaryHashForDbQuery,
            fileName = cpiFileName,
            fileChecksum = checksum.toString(),
            groupPolicy = cpi.metadata.groupPolicy!!,
            groupId = groupId,
            fileUploadRequestId = requestId,
            cpks = cpiCpkEntities
        )
    }

    private fun persistNewCpkFileEntities(cpiFileChecksum: String, em: EntityManager, cpks: Collection<Cpk>) {
        val existingCpkMap = em.createQuery(
            "FROM ${CpkFileEntity::class.java.simpleName} f WHERE f.fileChecksum IN :ids",
            CpkFileEntity::class.java
        )
            .setParameter("ids", cpks.map { it.metadata.fileChecksum.toString() })
            .resultList
            .associateBy { it.fileChecksum }

        val (existingCpks, newCpks) = cpks.partition { it.metadata.fileChecksum.toString() in existingCpkMap.keys }

        check(existingCpks.toSet().size == existingCpkMap.keys.size)

        newCpks.forEach {
            em.persist(CpkFileEntity(it.metadata.fileChecksum.toString(), Files.readAllBytes(it.path!!)))
        }

        if (existingCpks.isNotEmpty()) {
            log.info(
                "When persisting CPK files for CPI $cpiFileChecksum, ${existingCpks.size} file entities already existed with " +
                        "checksums ${existingCpkMap.keys.joinToString()}. No changes were made to these files."
            )
        }
    }

    override fun validateCanUpsertCpi(
        cpiName: String,
        cpiSignerSummaryHash: String,
        cpiVersion: String,
        groupId: String,
        forceUpload: Boolean,
        requestId: String
    ) {
        val sameCPis = entityManagerFactory.createEntityManager().transaction {
            it.createQuery(
                "FROM ${CpiMetadataEntity::class.simpleName} c " +
                        "WHERE c.name = :cpiName " +
                        "AND c.signerSummaryHash = :cpiSignerSummaryHash",
                CpiMetadataEntity::class.java
            )
                .setParameter("cpiName", cpiName)
                .setParameter("cpiSignerSummaryHash", cpiSignerSummaryHash).resultList
        }

        if (forceUpload) {
            if (!sameCPis.any { it.version == cpiVersion }) {
                throw ValidationException("No instance of same CPI with previous version found", requestId)
            }
            if (sameCPis.first().groupId != groupId) {
                throw ValidationException("Cannot force update a CPI with a different group ID", requestId)
            }
            // We can force-update this CPI because we found one with the same version
            return
        }

        // outside a force-update, anything goes except identical ID (name, signer and version)
        if (sameCPis.any { it.version == cpiVersion }) {
            throw DuplicateCpiUploadException("CPI $cpiName, $cpiVersion, $cpiSignerSummaryHash already exists.")
        }

        // NOTE: we may do additional validation here, such as validate that the group ID is not changing during a
        //  regular update. For now, just logging this as un-usual.
        if (sameCPis.any { it.groupId != groupId }) {
            log.info(
                "CPI upload $requestId contains a CPI with the same name ($cpiName) and " +
                        "signer ($cpiSignerSummaryHash) as an existing CPI, but a different Group ID."
            )
        }
    }
}
