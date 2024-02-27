package net.corda.virtualnode.write.db.impl.writer

import net.corda.libs.cpi.datamodel.repository.CpiMetadataRepository
import net.corda.libs.packaging.core.CpiIdentifier
import net.corda.libs.packaging.core.CpiMetadata
import net.corda.orm.utils.transaction
import net.corda.orm.utils.use
import org.slf4j.LoggerFactory
import javax.persistence.EntityManager
import javax.persistence.EntityManagerFactory

/** Reads and writes CPIs, holding identities and virtual nodes to and from the cluster database. */
// TODO - remove this when moving to repository pattern for everything.
//  This will likely be done as part of CORE-8744
internal class VirtualNodeEntityRepository(
    private val entityManagerFactory: EntityManagerFactory,
    private val cpiMetadataRepository: CpiMetadataRepository
) : CpiEntityRepository {

    private companion object {
        val log = LoggerFactory.getLogger(this::class.java.enclosingClass)
        private const val SHORT_HASH_LENGTH: Int = 12
    }

    /** Reads CPI metadata from the database. */
    override fun getCpiMetadataByChecksum(cpiFileChecksum: String): CpiMetadata? {
        if (cpiFileChecksum.isBlank()) {
            log.warn("CPI file checksum cannot be empty")
            return null
        }

        if (cpiFileChecksum.length < SHORT_HASH_LENGTH) {
            log.warn("CPI file checksum must be at least $SHORT_HASH_LENGTH characters")
            return null
        }

        return entityManagerFactory.transaction { em ->
            cpiMetadataRepository.findByFileChecksum(em, cpiFileChecksum)
        }
    }

    /** Reads CPI metadata from the database. */
    override fun getCPIMetadataByNameAndVersion(name: String, version: String): CpiMetadata? {
        val cpiMetadata = entityManagerFactory.createEntityManager().use {
            it.transaction { em ->
                cpiMetadataRepository.findByNameAndVersion(em, name, version)
            }
        }

        return cpiMetadata
    }

    override fun getCPIMetadataById(em: EntityManager, id: CpiIdentifier): CpiMetadata? {
        return cpiMetadataRepository.findById(em, id)
    }
}
