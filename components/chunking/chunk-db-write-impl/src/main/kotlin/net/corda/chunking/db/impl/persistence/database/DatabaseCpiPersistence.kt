package net.corda.chunking.db.impl.persistence.database

import java.nio.file.Files
import javax.persistence.EntityManager
import javax.persistence.EntityManagerFactory
import javax.persistence.LockModeType
import net.corda.chunking.RequestId
import net.corda.chunking.db.impl.persistence.CpiPersistence
import net.corda.libs.cpi.datamodel.CpkDbChangeLog
import net.corda.libs.cpi.datamodel.CpkDbChangeLogAudit
import net.corda.libs.cpi.datamodel.CpkFile
import net.corda.libs.cpi.datamodel.repository.CpiMetadataRepository
import net.corda.libs.cpi.datamodel.repository.CpkDbChangeLogAuditRepository
import net.corda.libs.cpi.datamodel.repository.CpkDbChangeLogRepository
import net.corda.libs.cpi.datamodel.repository.CpkFileRepository
import net.corda.libs.cpiupload.DuplicateCpiUploadException
import net.corda.libs.cpiupload.ValidationException
import net.corda.libs.packaging.Cpi
import net.corda.libs.packaging.Cpk
import net.corda.libs.packaging.core.CpiIdentifier
import net.corda.libs.packaging.core.CpiMetadata
import net.corda.membership.lib.grouppolicy.GroupPolicyParser
import net.corda.membership.lib.grouppolicy.GroupPolicyParser.Companion.isStaticNetwork
import net.corda.membership.network.writer.NetworkInfoWriter
import net.corda.orm.utils.transaction
import net.corda.v5.crypto.SecureHash
import org.slf4j.LoggerFactory

/**
 * This class provides some simple APIs to interact with the database for manipulating CPIs, CPKs and their associated
 * metadata.
 */
@Suppress("LongParameterList")
class DatabaseCpiPersistence(
    private val entityManagerFactory: EntityManagerFactory,
    private val networkInfoWriter: NetworkInfoWriter,
    private val cpiMetadataRepository: CpiMetadataRepository,
    private val cpkDbChangeLogRepository: CpkDbChangeLogRepository,
    private val cpkDbChangeLogAuditRepository: CpkDbChangeLogAuditRepository,
    private val cpkFileRepository: CpkFileRepository,
    private val groupPolicyParser: GroupPolicyParser.Companion
) : CpiPersistence {

    private companion object {
        val log = LoggerFactory.getLogger(this::class.java.enclosingClass)
    }

    /**
     * Check if we already have a cpk persisted with this checksum
     *
     * @return true if checksum exists in database
     */
    override fun cpkExists(cpkChecksum: SecureHash): Boolean {
        return entityManagerFactory.createEntityManager().transaction { em ->
            cpkFileRepository.exists(em, cpkChecksum)
        }
    }

    override fun cpiExists(cpiId: CpiIdentifier): Boolean =
        entityManagerFactory.createEntityManager().transaction { em ->
            getCpiMetadata(em, cpiId) != null
        }

    override fun persistMetadataAndCpks(
        cpi: Cpi,
        cpiFileName: String,
        cpiFileChecksum: SecureHash,
        requestId: RequestId,
        groupId: String,
        changelogsExtractedFromCpi: List<CpkDbChangeLog>
    ): CpiMetadata {
        entityManagerFactory.createEntityManager().transaction { em ->
            val groupPolicy = getGroupPolicy(em, cpi)

            val cpiMetadata = cpiMetadataRepository.update(
                em,
                cpi.metadata.cpiId,
                cpiFileName,
                cpiFileChecksum,
                groupId,
                groupPolicy,
                requestId,
                cpi.cpks
            )

            persistNewCpkFileEntities(em, cpi.metadata.fileChecksum, cpi.cpks)

            persistNewChangelogs(em, changelogsExtractedFromCpi)

            return@persistMetadataAndCpks cpiMetadata
        }
    }

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
        changelogsExtractedFromCpi: Collection<CpkDbChangeLog>
    ) {

        changelogsExtractedFromCpi.forEach { changelog ->
            log.info("Persisting changelog and audit for CPK: ${changelog.id.cpkFileChecksum}, ${changelog.id.filePath})")
            cpkDbChangeLogRepository.update(
                em,
                changelog
            )  // updating ensures any existing changelogs have isDeleted set to false
            cpkDbChangeLogAuditRepository.put(em, CpkDbChangeLogAudit(changeLog = changelog))
        }
    }

    override fun updateMetadataAndCpks(
        cpi: Cpi,
        cpiFileName: String,
        cpiFileChecksum: SecureHash,
        requestId: RequestId,
        groupId: String,
        changelogsExtractedFromCpi: Collection<CpkDbChangeLog>
    ): CpiMetadata {
        val cpiId = cpi.metadata.cpiId
        log.info("Performing updateMetadataAndCpks for: ${cpiId.name} v${cpiId.version}")

        // Perform update of CPI and store CPK along with its metadata
        entityManagerFactory.createEntityManager().transaction { em ->
            // We cannot delete old representation of CPI as there is FK constraint from `vnode_instance`
            val existingMetadata = requireNotNull(
                getCpiMetadata(em, cpiId)
            ) {
                "Cannot find CPI metadata for ${cpiId.name} v${cpiId.version}"
            }

            val cpiMetadata = cpiMetadataRepository.update(
                em,
                existingMetadata.cpiId,
                cpiFileName,
                cpiFileChecksum,
                groupId,
                existingMetadata.groupPolicy!!,
                requestId,
                cpi.cpks,
                existingMetadata.version
            )

            persistNewCpkFileEntities(em, cpiFileChecksum, cpi.cpks)

            persistNewChangelogs(em, changelogsExtractedFromCpi)

            return cpiMetadata
        }
    }

    override fun getGroupId(cpiId: CpiIdentifier): String? {
        entityManagerFactory.createEntityManager().transaction { em ->
            val groupPolicy = getCpiMetadata(em, cpiId)?.groupPolicy
                ?: return null
            return groupPolicyParser.groupIdFromJson(groupPolicy)
        }
    }

    private fun getCpiMetadata(em: EntityManager, cpiId: CpiIdentifier) =
        // In case of force update, we want the entity to change regardless of whether the CPI being uploaded
        //  is identical to an existing.
        //  OPTIMISTIC_FORCE_INCREMENT means the version number will always be bumped.
        cpiMetadataRepository.findById(em, cpiId, LockModeType.OPTIMISTIC_FORCE_INCREMENT)

    private fun persistNewCpkFileEntities(em: EntityManager, cpiFileChecksum: SecureHash, cpks: Collection<Cpk>) {
        val existingCpkMap =
            cpkFileRepository.findById(em, cpks.map { it.metadata.fileChecksum }).associateBy { it.fileChecksum }

        val (existingCpks, newCpks) = cpks.partition { it.metadata.fileChecksum in existingCpkMap.keys }

        check(existingCpks.toSet().size == existingCpkMap.keys.size)

        newCpks.forEach {
            cpkFileRepository.put(em, CpkFile(it.metadata.fileChecksum, Files.readAllBytes(it.path!!)))
        }

        if (existingCpks.isNotEmpty()) {
            log.info(
                "When persisting CPK files for CPI $cpiFileChecksum, ${existingCpks.size} file entities already " +
                        "existed with checksums ${existingCpkMap.keys.joinToString()}. No changes were made to these " +
                        "files."
            )
        }
    }

    override fun validateCanUpsertCpi(
        cpiId: CpiIdentifier,
        groupId: String,
        forceUpload: Boolean,
        requestId: String
    ) {
        val sameCPis = entityManagerFactory.createEntityManager().transaction { em ->
            cpiMetadataRepository.findByNameAndSignerSummaryHash(em, cpiId.name, cpiId.signerSummaryHash)
        }

        if (forceUpload) {
            if (!sameCPis.any { it.cpiId.version == cpiId.version }) {
                throw ValidationException("No instance of same CPI with previous version found", requestId)
            }

            if (groupPolicyParser.groupIdFromJson(sameCPis.first().groupPolicy!!) != groupId) {
                throw ValidationException("Cannot force update a CPI with a different group ID", requestId)
            }
            // We can force-update this CPI because we found one with the same version
            return
        }

        // outside a force-update, anything goes except identical ID (name, signer and version)
        if (sameCPis.any { it.cpiId.version == cpiId.version }) {
            throw DuplicateCpiUploadException("CPI ${cpiId.name}, ${cpiId.version}, ${cpiId.signerSummaryHash} already exists.")
        }

        // NOTE: we may do additional validation here, such as validate that the group ID is not changing during a
        //  regular update. For now, just logging this as un-usual.
        if (sameCPis.any { groupPolicyParser.groupIdFromJson(it.groupPolicy!!) != groupId }) {
            log.info(
                "CPI upload $requestId contains a CPI with the same name (${cpiId.name}) and " +
                        "signer (${cpiId.signerSummaryHash}) as an existing CPI, but a different Group ID."
            )
        }
    }

    /**
     * Process CPI network data and return the processed group policy.
     */
    private fun getGroupPolicy(
        em: EntityManager,
        cpi: Cpi
    ): String {
        val groupPolicy = cpi.metadata.groupPolicy!!
        return if (isStaticNetwork(groupPolicy)) {
            networkInfoWriter.parseAndPersistStaticNetworkInfo(em, cpi)
            networkInfoWriter.injectStaticNetworkMgm(em, groupPolicy)
        } else {
            groupPolicy
        }
    }
}
