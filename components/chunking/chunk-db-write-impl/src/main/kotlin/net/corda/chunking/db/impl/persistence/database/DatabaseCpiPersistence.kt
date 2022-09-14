package net.corda.chunking.db.impl.persistence.database

import net.corda.chunking.RequestId
import net.corda.chunking.db.impl.persistence.CpiPersistence
import net.corda.chunking.db.impl.persistence.PersistenceUtils.signerSummaryHashForDbQuery
import net.corda.chunking.db.impl.persistence.PersistenceUtils.toCpkKey
import net.corda.libs.cpi.datamodel.CpiCpkEntity
import net.corda.libs.cpi.datamodel.CpiCpkKey
import net.corda.libs.cpi.datamodel.CpiMetadataEntity
import net.corda.libs.cpi.datamodel.CpiMetadataEntityKey
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
import net.corda.libs.cpiupload.ValidationException
import net.corda.libs.packaging.Cpi
import net.corda.libs.packaging.Cpk
import net.corda.orm.utils.transaction
import net.corda.v5.base.util.contextLogger
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
                CpiCpkEntity(
                    CpiCpkKey(
                        cpi.metadata.cpiId.name, cpi.metadata.cpiId.version, cpi.metadata.cpiId.signerSummaryHash?.toString() ?: "",
                        // TODO Fallback to empty string can be removed after package verification is enabled (CORE-5405)
                        cpk.metadata.cpkId.name, cpk.metadata.cpkId.version, cpk.metadata.cpkId.signerSummaryHash?.toString().orEmpty()
                    ),
                    cpk.originalFileName!!,
                    cpk.metadata.fileChecksum.toString(),
                    CpkMetadataEntity(
                        cpk.metadata.cpkId.toCpkKey(),
                        cpk.metadata.fileChecksum.toString(),
                        cpk.metadata.manifest.cpkFormatVersion.toString(),
                        cpk.metadata.toJsonAvro()
                    )
                )
            }

            val cpiMetadataEntity = createCpiMetadataEntity(cpi, cpiFileName, checksum, requestId, groupId, cpiCpkEntities)

            val managedCpiMetadataEntity = em.merge(cpiMetadataEntity)

            createOrUpdateCpkFileEntities(em, cpi.cpks)

            updateChangeLogs(cpkDbChangeLogEntities, em)

            return@persistMetadataAndCpks managedCpiMetadataEntity
        }
    }

    private fun updateChangeLogs(
        cpkDbChangeLogEntities: List<CpkDbChangeLogEntity>,
        em: EntityManager
    ) {
        cpkDbChangeLogEntities.forEach {
            val inDb = em.find(CpkDbChangeLogEntity::class.java, it.id)
            if (inDb != null) it.entityVersion = inDb.entityVersion
            em.merge(it)
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

            updateChangeLogs(cpkDbChangeLogEntities, em)

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
                CpiCpkEntity(
                    CpiCpkKey(
                        cpi.metadata.cpiId.name, cpi.metadata.cpiId.version, cpi.metadata.cpiId.signerSummaryHash.toString(),
                        cpk.metadata.cpkId.name, cpk.metadata.cpkId.version, cpk.metadata.cpkId.signerSummaryHash.toString()
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

                    if (updatedEntities < 1)
                        throw OptimisticLockException(
                            "Updating ${CpkFileEntity::class.java.simpleName} with id $cpkKey failed due to " +
                                "optimistic lock version mismatch. Expected entityVersion ${existingCpkFile.entityVersion}."
                        )
                }
            } else {
                // the cpk doesn't exist so we'll persist a new file
                em.persist(
                    CpkFileEntity(cpkKey, cpk.metadata.fileChecksum.toString(), Files.readAllBytes(cpk.path!!))
                )
            }
        }
    }

    private fun checkForMatchingEntity(entitiesFound: List<CpiMetadataEntity>, cpiName: String, cpiVersion: String, requestId:String) {
        if (entitiesFound.singleOrNull { it.name == cpiName && it.version == cpiVersion } == null)
            throw ValidationException("No instance of same CPI with previous version found", requestId)
    }

    override fun canUpsertCpi(cpiName: String, groupId: String, forceUpload: Boolean, cpiVersion: String?, requestId:String): Boolean {
        val entitiesFound = entityManagerFactory.createEntityManager().transaction {
            it.createQuery(
                "FROM ${CpiMetadataEntity::class.simpleName} c WHERE c.groupId = :groupId",
                CpiMetadataEntity::class.java
            ).setParameter("groupId", groupId).resultList
        }

        // we can insert this CPI, it's the first one with this group id
        if (entitiesFound.isEmpty() && !forceUpload) return true

        if (forceUpload && cpiVersion != null) {
            checkForMatchingEntity(entitiesFound, cpiName, cpiVersion, requestId)
            // We can update this CPI if we find one with the same version in the case of a forceUpload
            return true
        } else if (entitiesFound.map { it.name }.contains(cpiName)) {
            // we can insert this CPI, it has the same name and group id (we are NOT checking the version)
            return true
        }

        return false
    }
}
