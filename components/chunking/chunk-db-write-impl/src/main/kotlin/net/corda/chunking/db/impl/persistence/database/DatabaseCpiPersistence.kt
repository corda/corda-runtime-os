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
import net.corda.libs.cpi.datamodel.CpkMetadataEntity
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
import javax.persistence.PersistenceException

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

    override fun store(
        cpi: Cpi,
        cpiFileName: String,
        checksum: SecureHash,
        requestId: RequestId,
        groupId: String,
        cpkDbChangeLogEntities: List<CpkDbChangeLogEntity>,
        allowCpiUpdate: Boolean
    ): CpiMetadataEntity {
        log.info("Storing CPI: ${cpi.metadata.cpiId.name} v${cpi.metadata.cpiId.version} (updates allowed=$allowCpiUpdate)")
        // Storing a CPI involves updating a number of tables:
        //  - CpkMedataEntity which stores the checksum and manifest of each CPK
        //  - CpkFileEntity which has the content of each CPK
        //  - CpkDbChangeLogEntity which has a row for each changelog resource file in any CPK
        //  - CpiCpkEntity which links CPKs to CPIs
        //  - CpiMetadataEntity which has the file-level metadata, group policy, group ID and file upload request ID
        //
        // Let's do all this within a transaction, so we complete it (or not) atomically.
        return entityManagerFactory.createEntityManager().transaction { em ->
            val cpiMetadataKey = CpiMetadataEntityKey(
                name = cpi.metadata.cpiId.name,
                version = cpi.metadata.cpiId.version,
                signerSummaryHash = cpi.metadata.cpiId.signerSummaryHashForDbQuery,
            )
            // There are 4 scenarios:
            // 1. This is a new CPI, and we are called via regular CPI upload API          => update DB
            // 2. This is a replacement CPI, and we are called via regular CPI upload API  => fail
            // 3. This is a new CPI, and we are called via force CPI upload API            => update DB
            // 4. This is a replacement CPI, and we are called via force CPI upload API    => update DB
            // i.e. sometimes we only want to allow creation of new CpiMetadataEntity records, not updating of existing ones
            // so there's a flag to control that behaviour. The criteria when updating is forbidden is that
            // cpiMetadataKey must not be an ID currently in the CpiMedataEntity table.
            // We do not care if some of the CPKs within it happen to be in the database already.
            val cpiMetadataInDb = em.find(CpiMetadataEntity::class.java, cpiMetadataKey)
            if (cpiMetadataInDb != null && !allowCpiUpdate) {
                // Case 2. We already have this CPI ID (the synthetic key of cpiMedataKey) and updates are
                // forbidden, so fail.
                throw PersistenceException(
                    "CPI has already been inserted with cpks for " +
                            "${cpi.metadata.cpiId.name} ${cpi.metadata.cpiId.version} with groupId=$groupId",
                )
            }

            // Okay, we do want to store this CPI and the CPKs within it. At this point, we are doing an update,
            // so we will preserve entityVersion values from all exisiting records.
            val cpiCpkEntities = cpi.cpks.mapTo(HashSet()) { cpk -> createCpiCpkEntity(cpk, em, cpi) }
            cpkDbChangeLogEntities.forEach {
                val inDb = em.find(CpkDbChangeLogEntity::class.java, it.id)
                em.merge(it.copy(entityVersion = inDb?.entityVersion ?: 0))
            }
            em.merge(
                CpiMetadataEntity(
                    cpiMetadataKey,
                    cpiFileName,
                    checksum.toString(),
                    cpi.metadata.groupPolicy!!,
                    groupId,
                    requestId,
                    cpiCpkEntities,
                    entityVersion = cpiMetadataInDb?.entityVersion ?: 0
                )
            )
        }
    }

    private fun createCpiCpkEntity(
        cpk: Cpk,
        em: EntityManager,
        cpi: Cpi
    ): CpiCpkEntity {
        val cpkMetadataKey = cpk.metadata.cpkId.toCpkKey()
        val cpkMetadataInDb = em.find(CpkMetadataEntity::class.java, cpkMetadataKey)
        val cpkMetadata = CpkMetadataEntity(
            cpkMetadataKey,
            cpk.metadata.fileChecksum.toString(),
            cpk.metadata.manifest.cpkFormatVersion.toString(),
            cpk.metadata.toJsonAvro(),
            false,
            cpkMetadataInDb?.entityVersion ?: 0
        )
        em.merge(cpkMetadata)
        val cpkFileInDb = em.find(CpkFileEntity::class.java, cpkMetadataKey)
        val cpkFile = CpkFileEntity(
            cpkMetadataKey, cpk.metadata.fileChecksum.toString(), Files.readAllBytes(cpk.path!!),
            entityVersion = cpkFileInDb?.entityVersion ?: 0
        )
        em.merge(cpkFile)
        val cpiCpkKey = CpiCpkKey(
            cpi.metadata.cpiId.name,
            cpi.metadata.cpiId.version,
            // TODO Fallback to empty string can be removed after package verification is enabled (CORE-5405)
            cpi.metadata.cpiId.signerSummaryHash?.toString() ?: "",
            cpk.metadata.cpkId.name,
            cpk.metadata.cpkId.version,
            cpk.metadata.cpkId.signerSummaryHash?.toString().orEmpty()
        )
        val cpiCpkInDb = em.find(CpiCpkEntity::class.java, cpiCpkKey)
        // we cannot persist/merge the new CpiCpkEntity yet since at this point it would not have a
        // CPI parent and that violates a foreign key constraint. Instead, we return it and the
        // store method which called us will store it as part of the CPI.
        return CpiCpkEntity(
            cpiCpkKey,
            cpkFileName = cpk.originalFileName!!,
            cpkFileChecksum = cpk.metadata.fileChecksum.toString(),
            metadata = cpkMetadata,
            entityVersion = cpiCpkInDb?.entityVersion ?: 0
        )
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

    private fun getCpiMetadataEntity(name: String, version: String, signerSummaryHash: String): CpiMetadataEntity? =
        entityManagerFactory.createEntityManager().transaction {
            findCpiMetadataEntityInTransaction(it, name, version, signerSummaryHash)
        }

    override fun getGroupId(cpiName: String, cpiVersion: String, signerSummaryHash: String): String? {
        return getCpiMetadataEntity(cpiName, cpiVersion, signerSummaryHash)?.groupId
    }

    private fun checkForMatchingEntity(
        entitiesFound: List<CpiMetadataEntity>,
        cpiName: String,
        cpiVersion: String,
    ) {
        if (entitiesFound.singleOrNull { it.id.name == cpiName && it.id.version == cpiVersion } == null)
            throw PersistenceException("No instance of same CPI with previous version found")
    }

    override fun canUpsertCpi(
        cpiName: String,
        groupId: String,
        forceUpload: Boolean,
        cpiVersion: String?,
        requestId: String
    ): Boolean {
        val entitiesFound = entityManagerFactory.createEntityManager().transaction {
            it.createQuery(
                "FROM ${CpiMetadataEntity::class.simpleName} c WHERE c.groupId = :groupId",
                CpiMetadataEntity::class.java
            ).setParameter("groupId", groupId).resultList
        }

        // we can insert this CPI, it's the first one with this group id
        if (entitiesFound.isEmpty() && !forceUpload) return true

        if (forceUpload && cpiVersion != null) {
            checkForMatchingEntity(entitiesFound, cpiName, cpiVersion)
            // We can update this CPI if we find one with the same version in the case of a forceUpload
            return true
        } else if (entitiesFound.map { it.id.name }.contains(cpiName)) {
            // we can insert this CPI, it has the same name and group id (we are NOT checking the version)
            return true
        }

        return false
    }
}