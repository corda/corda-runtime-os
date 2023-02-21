package net.corda.libs.cpi.datamodel.repository

import net.corda.libs.cpi.datamodel.CpiCpk
import net.corda.libs.cpi.datamodel.entities.CpiMetadataEntity
import net.corda.libs.packaging.core.CpiIdentifier
import net.corda.libs.packaging.core.CpiMetadata
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

    fun findById(em: EntityManager, id: CpiIdentifier, lockModeType: LockModeType = LockModeType.NONE): CpiMetadata?

    fun findByNameAndCpiSignerSummaryHash(em: EntityManager, cpiName: String, cpiSignerSummaryHash: String): List<CpiMetadata>

    fun findByNameAndVersion(em: EntityManager, name: String, version: String): CpiMetadata

    fun findByChecksum(em: EntityManager, cpiFileChecksum: String): CpiMetadata?

    fun update(
        em: EntityManager,
        cpiId: CpiIdentifier,
        fileName: String,
        fileChecksum: SecureHash,
        groupPolicy: String,
        groupId: String,
        requestId: String,
        cpiCpk: Set<CpiCpk>
    ): CpiMetadata

    fun update(
        em: EntityManager,
        cpiMetadata: CpiMetadata,
        fileName: String,
        fileChecksum: SecureHash,
        requestId: String,
        groupId: String,
        cpiCpk: Set<CpiCpk>
    ): CpiMetadata
}

