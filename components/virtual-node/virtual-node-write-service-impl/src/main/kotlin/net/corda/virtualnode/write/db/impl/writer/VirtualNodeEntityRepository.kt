package net.corda.virtualnode.write.db.impl.writer

import net.corda.libs.cpi.datamodel.repository.CpiMetadataRepositoryImpl
import net.corda.libs.packaging.core.CpiIdentifier
import net.corda.libs.packaging.core.CpiMetadata
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
        private val cpiMetadataRepository = CpiMetadataRepositoryImpl()
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

        return entityManagerFactory.transaction {
            cpiMetadataRepository.findByChecksum(it, cpiFileChecksum)?.toLite()
        }
    }

    /** Reads CPI metadata from the database. */
    internal fun getCPIMetadataById(name: String, version: String): CpiMetadataLite {
        return entityManagerFactory.use {
            cpiMetadataRepository.findByNameAndVersion(it, name, version).toLite()
        }
    }

    internal fun getCPIMetadataById(
        em: EntityManager, name: String, version: String, signerSummaryHash: SecureHash
    ): CpiMetadataLite? {
        return cpiMetadataRepository.findById(em, CpiIdentifier(name, version, signerSummaryHash))?.toLite()
    }

    private fun CpiMetadata.toLite(): CpiMetadataLite {
        val cpiId = CpiIdentifier(cpiId.name, cpiId.version, cpiId.signerSummaryHash)
        return CpiMetadataLite(cpiId, fileChecksum.toHexString(), groupId, groupPolicy, "", "", emptySet())
    }
}
