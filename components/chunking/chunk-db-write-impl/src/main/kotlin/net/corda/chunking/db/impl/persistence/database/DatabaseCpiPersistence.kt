package net.corda.chunking.db.impl.persistence.database

import net.corda.chunking.RequestId
import net.corda.chunking.db.impl.persistence.CpiPersistence
import net.corda.chunking.db.impl.persistence.PersistenceUtils.signerSummaryHashForDbQuery
import net.corda.chunking.db.impl.persistence.PersistenceUtils.toCpkKey
import net.corda.libs.cpi.datamodel.CpiCpkEntity
import net.corda.libs.cpi.datamodel.CpiCpkKey
import net.corda.libs.cpi.datamodel.CpiMetadataEntity
import net.corda.libs.cpi.datamodel.CpiMetadataEntityKey
import net.corda.libs.cpi.datamodel.CpkDbChangeLogAuditEntity
import net.corda.libs.cpi.datamodel.CpkDbChangeLogEntity
import net.corda.libs.cpi.datamodel.CpkFileEntity
import net.corda.libs.cpi.datamodel.CpkKey
import net.corda.libs.cpi.datamodel.CpkMetadataEntity
import net.corda.libs.cpi.datamodel.QUERY_NAME_UPDATE_CPK_FILE_DATA
import net.corda.libs.cpi.datamodel.QUERY_PARAM_DATA
import net.corda.libs.cpi.datamodel.QUERY_PARAM_ENTITY_VERSION
import net.corda.libs.cpi.datamodel.QUERY_PARAM_FILE_CHECKSUM
import net.corda.libs.cpi.datamodel.QUERY_PARAM_ID
import net.corda.libs.cpi.datamodel.QUERY_PARAM_INCREMENTED_ENTITY_VERSION
import net.corda.libs.cpi.datamodel.findDbChangeLogForCpi
import net.corda.libs.cpiupload.DuplicateCpiUploadException
import net.corda.libs.cpiupload.ValidationException
import net.corda.libs.packaging.Cpi
import net.corda.libs.packaging.Cpk
import net.corda.orm.utils.transaction
import net.corda.v5.base.util.contextLogger
import net.corda.v5.base.util.debug
import net.corda.v5.crypto.SecureHash
import java.nio.file.Files
import javax.persistence.EntityManager
import javax.persistence.EntityManagerFactory
import javax.persistence.LockModeType
import javax.persistence.NonUniqueResultException
import javax.persistence.OptimisticLockException

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
        checksum: SecureHash,
        requestId: RequestId,
        groupId: String,
        cpkDbChangeLogEntities: List<CpkDbChangeLogEntity>
    ): CpiMetadataEntity {
        entityManagerFactory.createEntityManager().transaction { em ->

            val cpiCpkEntities = cpi.cpks.mapTo(HashSet()) { cpk ->
                val cpiCpkKey = CpiCpkKey(
                    cpi.metadata.cpiId.name,
                    cpi.metadata.cpiId.version,
                    cpi.metadata.cpiId.signerSummaryHash?.toString() ?: "",
                    // TODO Fallback to empty string can be removed after package verification is enabled (CORE-5405)
                    cpk.metadata.cpkId.name,
                    cpk.metadata.cpkId.version,
                    cpk.metadata.cpkId.signerSummaryHash.toString()
                )
                val cpiCpkInDb = em.find(CpiCpkEntity::class.java, cpiCpkKey)
                val cpkMetadataKey = cpk.metadata.cpkId.toCpkKey()
                val cpkMetadataInDb = em.find(CpkMetadataEntity::class.java, cpkMetadataKey)
                CpiCpkEntity(
                    cpiCpkKey,
                    cpk.originalFileName!!,
                    cpk.metadata.fileChecksum.toString(),
                    CpkMetadataEntity(
                        cpkMetadataKey,
                        cpk.metadata.fileChecksum.toString(),
                        cpk.metadata.manifest.cpkFormatVersion.toString(),
                        cpk.metadata.toJsonAvro(),
                        entityVersion = cpkMetadataInDb?.entityVersion ?: 0
                    ),
                    cpiCpkInDb?.entityVersion ?: 0
                )
            }

            val cpiMetadataEntity = createCpiMetadataEntity(cpi, cpiFileName, checksum, requestId, groupId, cpiCpkEntities)

            val managedCpiMetadataEntity = em.merge(cpiMetadataEntity)

            createOrUpdateCpkFileEntities(em, cpi.cpks)

            updateChangeLogs(cpkDbChangeLogEntities, em, cpi)

            return@persistMetadataAndCpks managedCpiMetadataEntity
        }
    }

    /**
     * Update the changelogs in the db for cpi upload
     *
     * @property cpkDbChangeLogEntities: [List]<[CpkDbChangeLogEntity]> a list of changelogs extracted from the force
     *  uploaded cpi.
     * @property em: [EntityManager] the entity manager from the call site. We reuse this for several operations as part
     *  of CPI upload
     * @property cpi: [Cpi] is the Cpi that has just been uploaded
     *
     * @return [Boolean] indicating whether we actually updated any changelogs
     */
    private fun updateChangeLogs(
        cpkDbChangeLogEntities: List<CpkDbChangeLogEntity>,
        em: EntityManager,
        cpi: Cpi
    ) {
        // The incoming changelogs will not be marked deleted
        cpkDbChangeLogEntities.forEach { require(!it.isDeleted) }
        val dbChangelogs = findDbChangeLogForCpi(em, cpi.metadata.cpiId).associateBy { it.id }
        val changeLogUpdates = cpkDbChangeLogEntities.associateBy { it.id }

        // Then, for the currently declared changelogs, we'll save the record and clear any isDeleted flags.
        // This all happens under one transaction so no one will see the isDeleted flags flicker.
        (dbChangelogs + changeLogUpdates)
            .entries
            .forEach { (changelogId, changelog) ->
                val inDb = em.find(CpkDbChangeLogEntity::class.java, changelogId)
                // Keep track of what updated version we persist.
                //  Also simulate the bumped entity version where appropriate.
                val ret: CpkDbChangeLogEntity? = if (inDb != null) {
                    changelog.entityVersion = inDb.entityVersion
                    // Check prior to merge
                    val hasChanged = changelog.fileChecksum != inDb.fileChecksum ||
                        changelog.changesetId != inDb.changesetId
                    if (changeLogUpdates.containsKey(changelogId) || hasChanged) {
                        // Mark as not deleted if this is one of the new entries
                        changelog.isDeleted = false
                    } else {
                        // Otherwise we assume that it's out of date and should be marked as deleted
                        changelog.isDeleted = true
                    }
                    em.merge(changelog)
                    if (hasChanged) {
                        // Simulate entityVersion increase
                        changelog.entityVersion += 1
                        // Return changelog
                        changelog
                    } else {
                        // There's no new audit entry required as there hasn't been an update
                        null
                    }
                } else {
                    em.persist(changelog)
                    // Return changelog
                    changelog
                }
                if (ret != null) {
                    log.debug {
                        "Creating new audit entry for ${ret.id.cpkName}:${ret.id.cpkVersion} with signer summary " +
                            "hash of ${ret.id.cpkSignerSummaryHash}} at entity version ${ret.entityVersion} " +
                            "with checksum ${ret.fileChecksum}"
                    }
                    val audit = CpkDbChangeLogAuditEntity(ret)
                    em.persist(audit)
                }
            }
    }

    override fun updateMetadataAndCpks(
        cpi: Cpi,
        cpiFileName: String,
        checksum: SecureHash,
        requestId: RequestId,
        groupId: String,
        cpkDbChangeLogEntities: List<CpkDbChangeLogEntity>
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

            val cpiCpkEntities = createOrUpdateCpiCpkEntities(cpi, existingMetadataEntity.cpks.associateBy { it.metadata.id })

            val updatedMetadata = existingMetadataEntity.update(
                fileUploadRequestId = requestId,
                fileName = cpiFileName,
                fileChecksum = checksum.toString(),
                cpks = cpiCpkEntities
            )

            val cpiMetadataEntity = em.merge(updatedMetadata)

            createOrUpdateCpkFileEntities(em, cpi.cpks)

            updateChangeLogs(cpkDbChangeLogEntities, em, cpi)

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

    private fun createOrUpdateCpiCpkEntities(cpi: Cpi, existingCpks: Map<CpkKey, CpiCpkEntity>): Set<CpiCpkEntity> {
        return cpi.cpks.mapTo(HashSet()) { cpk ->
            val cpkKey = cpk.metadata.cpkId.toCpkKey()
            val entityToUpdate = existingCpks[cpkKey]

            if (entityToUpdate != null) {
                cpk.originalFileName?.let { entityToUpdate.cpkFileName = it }
                entityToUpdate.cpkFileChecksum = cpk.metadata.fileChecksum.toString()
                entityToUpdate.isDeleted = false
                entityToUpdate.metadata.cpkFileChecksum = cpk.metadata.fileChecksum.toString()
                entityToUpdate.metadata.serializedMetadata = cpk.metadata.toJsonAvro()
                entityToUpdate.metadata.formatVersion = cpk.metadata.manifest.cpkFormatVersion.toString()
                entityToUpdate
            } else {
                // Is it possible that this code can fail with OptimisticLockException due to not setting
                // entityVersion in CpiCpkEntity or CpkMetadataEntity?
                // (better to eliminate the duplication with similar logic in persistMetadataAndCpks
                CpiCpkEntity(
                    CpiCpkKey(
                        cpi.metadata.cpiId.name,
                        cpi.metadata.cpiId.version,
                        cpi.metadata.cpiId.signerSummaryHashForDbQuery,
                        cpk.metadata.cpkId.name,
                        cpk.metadata.cpkId.version,
                        cpk.metadata.cpkId.signerSummaryHashForDbQuery
                    ),
                    cpk.originalFileName!!,
                    cpk.metadata.fileChecksum.toString(),
                    CpkMetadataEntity(
                        cpkKey,
                        cpk.metadata.fileChecksum.toString(),
                        cpk.metadata.manifest.cpkFormatVersion.toString(),
                        cpk.metadata.toJsonAvro()
                    )
                )
            }
        }
    }

    data class CpkFileEntityQueryResult(val id: CpkKey, val fileChecksum: String, val entityVersion: Int)

    private fun createOrUpdateCpkFileEntities(em: EntityManager, cpks: Collection<Cpk>) {
        val query = """
            SELECT f.id, f.fileChecksum, f.entityVersion 
            from ${CpkFileEntity::class.java.simpleName} f  
            where f.id IN :ids
        """.trimIndent()
        val existingCpkFiles = em.createQuery(query)
            .setLockMode(LockModeType.OPTIMISTIC_FORCE_INCREMENT)
            .setParameter("ids", cpks.map { it.metadata.cpkId.toCpkKey() })
            .resultList
            .associate {
                it as Array<*>
                val cpkKey = it[0] as CpkKey
                val fileChecksum = it[1] as String
                val entityVersion = it[2] as Int
                cpkKey to CpkFileEntityQueryResult(cpkKey, fileChecksum, entityVersion)
            }

        cpks.map { cpk ->
            val cpkKey = cpk.metadata.cpkId.toCpkKey()
            val existingCpkFile = existingCpkFiles[cpkKey]

            if (existingCpkFile != null) {
                // the cpk exists already, lets update it if the file checksum has changed.
                if (existingCpkFile.fileChecksum != cpk.metadata.fileChecksum.toString()) {
                    val updatedEntities = em.createNamedQuery(QUERY_NAME_UPDATE_CPK_FILE_DATA)
                        .setParameter(QUERY_PARAM_FILE_CHECKSUM, cpk.metadata.fileChecksum.toString())
                        .setParameter(QUERY_PARAM_DATA, Files.readAllBytes(cpk.path!!))
                        .setParameter(QUERY_PARAM_ENTITY_VERSION, existingCpkFile.entityVersion)
                        .setParameter(QUERY_PARAM_INCREMENTED_ENTITY_VERSION, existingCpkFile.entityVersion + 1)
                        .setParameter(QUERY_PARAM_ID, cpkKey)
                        .executeUpdate()

                    if (updatedEntities < 1) {
                        throw OptimisticLockException(
                            "Updating ${CpkFileEntity::class.java.simpleName} with id $cpkKey failed due to " +
                                "optimistic lock version mismatch. Expected entityVersion ${existingCpkFile.entityVersion}."
                        )
                    }
                }
            } else {
                // the cpk doesn't exist so we'll persist a new file
                em.persist(
                    CpkFileEntity(cpkKey, cpk.metadata.fileChecksum.toString(), Files.readAllBytes(cpk.path!!))
                )
            }
        }
    }

    override fun validateCanUpsertCpi(
        cpiName: String,
        cpiSignerSummaryHash: String,
        cpiVersion: String,
        groupId: String,
        forceUpload: Boolean,
        requestId: String) {
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
            if(sameCPis.first().groupId != groupId) {
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
        if(sameCPis.any { it.groupId != groupId }) {
            log.info("CPI upload $requestId contains a CPI with the same name ($cpiName) and " +
                    "signer ($cpiSignerSummaryHash) as an existing CPI, but a different Group ID.")
        }
    }
}
