package net.corda.chunking.db.impl.persistence.database

import net.corda.chunking.RequestId
import net.corda.chunking.db.impl.persistence.CpiPersistence
import net.corda.libs.cpi.datamodel.*
import net.corda.libs.cpi.datamodel.repository.*
import net.corda.libs.cpiupload.DuplicateCpiUploadException
import net.corda.libs.cpiupload.ValidationException
import net.corda.libs.packaging.Cpi
import net.corda.libs.packaging.Cpk
import net.corda.libs.packaging.core.*
import net.corda.orm.utils.transaction
import net.corda.v5.crypto.SecureHash
import org.slf4j.LoggerFactory
import java.nio.file.Files
import javax.persistence.EntityManager
import javax.persistence.EntityManagerFactory
import javax.persistence.LockModeType

/**
 * This class provides some simple APIs to interact with the database for manipulating CPIs, CPKs and their associated metadata.
 */
class DatabaseCpiPersistence(private val entityManagerFactory: EntityManagerFactory) : CpiPersistence {

    private companion object {
        val log = LoggerFactory.getLogger(this::class.java.enclosingClass)
        val cpkDbChangeLogRepository = CpkDbChangeLogRepositoryImpl()
        val cpkDbChangeLogAuditRepository = CpkDbChangeLogAuditRepositoryImpl()
        val cpkFileRepository = CpkFileRepositoryImpl()
        val cpiMetadataRepository = CpiMetadataRepositoryImpl()
        val cpkMetadataRepository = CpkMetadataRepositoryImpl()
        val cpiCpkRepository = CpiCpkRepositoryImpl()
    }

    /**
     * Check if we already have a cpk persisted with this checksum
     *
     * @return true if checksum exists in database
     */
    override fun cpkExists(cpkChecksum: SecureHash): Boolean {
        return entityManagerFactory.createEntityManager().transaction {
            cpkFileRepository.exists(it, cpkChecksum)
        }
    }

    override fun cpiExists(cpiId: CpiIdentifier): Boolean =
        getCpiMetadataEntity(cpiId) != null

    override fun persistMetadataAndCpks(
        cpi: Cpi,
        cpiFileName: String,
        cpiFileChecksum: SecureHash,
        requestId: RequestId,
        groupId: String,
        changelogsExtractedFromCpi: List<CpkDbChangeLog>
    ): CpiMetadata {
        entityManagerFactory.createEntityManager().transaction { em ->

            val cpiMetadata = cpiMetadataRepository.update(
                em,
                cpi.metadata.cpiId,
                cpiFileName,
                cpiFileChecksum,
                cpi.metadata.groupPolicy,
                groupId,
                requestId,
                createCpiCpkRelationships(em, cpi),
            )

            persistNewCpkFileEntities(em, cpi.metadata.fileChecksum, cpi.cpks)
            persistNewChangelogs(em, changelogsExtractedFromCpi)

            return@persistMetadataAndCpks cpiMetadata
        }
    }

    private fun createCpiCpkRelationships(em: EntityManager, cpi: Cpi): Set<CpiCpk> {
        // there may be some CPKs that already exist. We should load these first, then create CpiCpk associations for them (if necessary).
        val foundCpks = cpkMetadataRepository.findById(em, cpi.cpks.map { it.metadata.fileChecksum }).associateBy { it.cpkFileChecksum }


        val (existingCpks, newCpks) = cpi.cpks.partition { it.metadata.fileChecksum in foundCpks.keys }

        val newCpiCpkRelationships = newCpks.map { cpk -> genCpiCpk(cpi, cpk) }

        check(foundCpks.keys.size == existingCpks.toSet().size)
        check(foundCpks.keys == existingCpks.map { it.metadata.fileChecksum }.toSet())

        val relationshipsForExistingCpks = existingCpks.map { cpk ->
            val cpiCpkId = genCpiCpkId(cpi, cpk)

            // cpiCpk relationship might already exist for this CPI, for example, if a force uploaded CPI doesn't change a CPK, otherwise
            // create a new one with the CpkMetadataEntity
            cpiCpkRepository.findById(em, cpiCpkId)
                ?: CpiCpk(
                    cpiCpkId,
                    cpk.originalFileName!!,
                    foundCpks[cpk.metadata.fileChecksum]!!
                )
        }

        val totalCpiCpkRelationships = newCpiCpkRelationships + relationshipsForExistingCpks
        return totalCpiCpkRelationships.toSet()
    }

    private fun genCpiCpkId(cpi: Cpi, cpk: Cpk) = CpiCpkIdentifier(
            cpi.metadata.cpiId.name,
            cpi.metadata.cpiId.version,
            cpi.metadata.cpiId.signerSummaryHash,
            cpk.metadata.fileChecksum
        )

    private fun genCpiCpk(cpi: Cpi, cpk:Cpk) = CpiCpk(
        genCpiCpkId(cpi, cpk),
        cpk.originalFileName!!,
        CpkMetadataLite(
            CpkIdentifier(
                cpk.metadata.cpkId.name,
                cpk.metadata.cpkId.version,
                cpk.metadata.cpkId.signerSummaryHash,
            ),
            cpk.metadata.fileChecksum,
            cpk.metadata.manifest.cpkFormatVersion,
            cpk.metadata.toJsonAvro()
        )
    )

    /**
     * Update the changelogs in the db for cpi upload
     *
     * @property changelogsExtractedFromCpi: [List]<[CpkDbChangeLog]> a list of changelogs extracted from the force
     *  uploaded cpi.
     * @property em: [EntityManager] the entity manager from the call site. We reuse this for several operations as part
     *  of CPI upload
     *
     * @return [Boolean] indicating whether we actually updated any changelogs
     */
    private fun persistNewChangelogs(
        em: EntityManager,
        changelogsExtractedFromCpi: List<CpkDbChangeLog>
    ) {

        changelogsExtractedFromCpi.forEach { changelog ->
            log.info("Persisting changelog and audit for CPK: ${changelog.fileChecksum}, ${changelog.filePath})")
            cpkDbChangeLogRepository.update(em, changelog)  // updating ensures any existing changelogs have isDeleted set to false
            cpkDbChangeLogAuditRepository.put(em, CpkDbChangeLogAudit(changeLog = changelog))
        }
    }

    override fun updateMetadataAndCpks(
        cpi: Cpi,
        cpiFileName: String,
        cpiFileChecksum: SecureHash,
        requestId: RequestId,
        groupId: String,
        changelogsExtractedFromCpi: List<CpkDbChangeLog>
    ): CpiMetadata {
        val cpiId = cpi.metadata.cpiId
        log.info("Performing updateMetadataAndCpks for: ${cpiId.name} v${cpiId.version}")

        // Perform update of CPI and store CPK along with its metadata
        entityManagerFactory.createEntityManager().transaction { em ->
            // We cannot delete old representation of CPI as there is FK constraint from `vnode_instance`
            val existingCpiMetadata = requireNotNull(
                findCpiMetadataEntityInTransaction(em, cpiId)
            ) {
                "Cannot find CPI metadata for ${cpiId.name} v${cpiId.version}"
            }

            val updatedCpiMetadata = cpiMetadataRepository.update(
                em,
                existingCpiMetadata,
                cpiFileName,
                cpiFileChecksum,
                requestId,
                groupId,
                createCpiCpkRelationships(em, cpi)
            )

            persistNewCpkFileEntities(em, cpiFileChecksum, cpi.cpks)

            persistNewChangelogs(em, changelogsExtractedFromCpi)

            return updatedCpiMetadata
        }
    }

    /**
     * @return null if not found
     */
    private fun findCpiMetadataEntityInTransaction(
        em: EntityManager,
        cpiId: CpiIdentifier
    ): CpiMetadata? {

        // In case of force update, we want the entity to change regardless of whether the CPI being uploaded
        //  is identical to an existing.
        //  OPTIMISTIC_FORCE_INCREMENT means the version number will always be bumped.
        return cpiMetadataRepository.findById(em, cpiId, LockModeType.OPTIMISTIC_FORCE_INCREMENT)
    }

    private fun getCpiMetadataEntity(cpiId: CpiIdentifier): CpiMetadata? {
        return entityManagerFactory.createEntityManager().transaction {
            findCpiMetadataEntityInTransaction(it, cpiId)
        }
    }

    // Todo: Try removing this method
    override fun getGroupId(cpiId: CpiIdentifier): String? {
        return getCpiMetadataEntity(cpiId)?.groupId
    }

    private fun persistNewCpkFileEntities( em: EntityManager, cpiFileChecksum: SecureHash, cpks: Collection<Cpk>) {
        val existingCpkMap = cpkFileRepository.findById(em, cpks.map { it.metadata.fileChecksum }).associateBy { it.fileChecksum }

        val (existingCpks, newCpks) = cpks.partition { it.metadata.fileChecksum in existingCpkMap.keys }

        check(existingCpks.toSet().size == existingCpkMap.keys.size)

        newCpks.forEach {
            cpkFileRepository.put(em, CpkFile(it.metadata.fileChecksum, Files.readAllBytes(it.path!!)))
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
            cpiMetadataRepository.findByNameAndCpiSignerSummaryHash(it, cpiName, cpiSignerSummaryHash)
        }

        if (forceUpload) {
            if (!sameCPis.any { it.cpiId.version == cpiVersion }) {
                throw ValidationException("No instance of same CPI with previous version found", requestId)
            }
            if (sameCPis.first().groupId != groupId) {
                throw ValidationException("Cannot force update a CPI with a different group ID", requestId)
            }
            // We can force-update this CPI because we found one with the same version
            return
        }

        // outside a force-update, anything goes except identical ID (name, signer and version)
        if (sameCPis.any { it.cpiId.version == cpiVersion }) {
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
