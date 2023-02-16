package net.corda.libs.cpi.datamodel.repository

import net.corda.libs.cpi.datamodel.entities.CpiMetadataEntity
import net.corda.libs.packaging.core.CpiIdentifier
import net.corda.libs.packaging.core.CpiMetadata
import net.corda.libs.packaging.core.CpkMetadata
import net.corda.v5.crypto.SecureHash
import java.util.stream.Stream
import javax.persistence.EntityManager
import javax.persistence.LockModeType

/**
 * Interface for CRUD operations for cpi metadata
 */
interface CpiMetadataRepository {
    /**
     * Find all cpi metadata.
     */
    fun findAll(em: EntityManager): Stream<CpiMetadata>

    fun findById(em: EntityManager, id: CpiIdentifier): CpiMetadata?

    fun findByNameAndCpiSignerSummaryHash(em: EntityManager, cpiName: String, cpiSignerSummaryHash: String): List<CpiMetadata>

    fun findByNameAndVersion(em: EntityManager, name: String, version: String): CpiMetadata

    fun findByChecksum(em: EntityManager, cpiFileChecksum: String): CpiMetadata?
}

