package net.corda.virtualnode.write.db.impl.writer

import net.corda.libs.cpi.datamodel.CpiMetadataEntity
import net.corda.libs.cpi.datamodel.CpiMetadataEntityKey
import net.corda.libs.packaging.core.CpiIdentifier
import net.corda.orm.utils.transaction
import net.corda.orm.utils.use
import net.corda.v5.crypto.SecureHash
import org.slf4j.LoggerFactory
import javax.persistence.EntityManager
import javax.persistence.EntityManagerFactory

/** Reads and writes CPIs, holding identities and virtual nodes to and from the cluster database. */
// TODO - remove this when moving to repository pattern for everything.
//  This will likely be done as part of CORE-8744
internal class VirtualNodeEntityRepository(
    val entityManagerFactory: EntityManagerFactory
    ) {

    private companion object {
        val log = LoggerFactory.getLogger(this::class.java.enclosingClass)
        private const val SHORT_HASH_LENGTH: Int = 12
    }

    /** Reads CPI metadata from the database. */
    internal fun getCpiMetadataByChecksum(cpiFileChecksum: String): CpiMetadataLite? {
        if (cpiFileChecksum.isBlank()) {
            log.warn("CPI file checksum cannot be empty")
            return null
        }

        if (cpiFileChecksum.length < SHORT_HASH_LENGTH) {
            log.warn("CPI file checksum must be at least $SHORT_HASH_LENGTH characters")
            return null
        }

        val cpiMetadataEntity = entityManagerFactory.transaction {
            val foundCpi = it.createQuery(
                "SELECT cpi FROM CpiMetadataEntity cpi " +
                    "WHERE upper(cpi.fileChecksum) like :cpiFileChecksum ",
                CpiMetadataEntity::class.java
            )
                .setParameter("cpiFileChecksum", "%${cpiFileChecksum.uppercase()}%")
                .resultList
            if (foundCpi.isNotEmpty()) foundCpi[0] else null
        } ?: return null

        val signerSummaryHash = SecureHash.parse(cpiMetadataEntity.signerSummaryHash)
        val cpiId = CpiIdentifier(cpiMetadataEntity.name, cpiMetadataEntity.version, signerSummaryHash)
        val fileChecksum = SecureHash.parse(cpiMetadataEntity.fileChecksum).toHexString()
        return CpiMetadataLite(cpiId, fileChecksum, cpiMetadataEntity.groupId, cpiMetadataEntity.groupPolicy)
    }

    /** Reads CPI metadata from the database. */
    internal fun getCPIMetadataByNameAndVersion(name: String, version: String): CpiMetadataLite? {
        val cpiMetadataEntity = entityManagerFactory.use {
            it.transaction {
                it.createQuery(
                    "SELECT cpi FROM CpiMetadataEntity cpi " +
                            "WHERE cpi.name = :cpiName "+
                            "AND cpi.version = :cpiVersion ",
                    CpiMetadataEntity::class.java
                )
                    .setParameter("cpiName", name)
                    .setParameter("cpiVersion", version)
                    .singleResult
            }
        }

        val signerSummaryHash = SecureHash.parse(cpiMetadataEntity.signerSummaryHash)
        val cpiId = CpiIdentifier(cpiMetadataEntity.name, cpiMetadataEntity.version, signerSummaryHash)
        val fileChecksum = SecureHash.parse(cpiMetadataEntity.fileChecksum).toHexString()
        return CpiMetadataLite(cpiId, fileChecksum, cpiMetadataEntity.groupId, cpiMetadataEntity.groupPolicy)
    }

    internal fun getCPIMetadataByNameAndVersion(
        em: EntityManager, name: String, version: String, signerSummaryHash: String
    ): CpiMetadataLite? {
        return em.find(
            CpiMetadataEntity::class.java,
            CpiMetadataEntityKey(name, version, signerSummaryHash)
        )?.toLite()
    }

    private fun CpiMetadataEntity.toLite(): CpiMetadataLite {
        val cpiId = CpiIdentifier(name, version, SecureHash.parse(signerSummaryHash))
        val fileChecksum = SecureHash.parse(fileChecksum).toHexString()
        return CpiMetadataLite(cpiId, fileChecksum, groupId, groupPolicy)
    }
}
