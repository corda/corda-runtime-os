package net.corda.libs.cpi.datamodel.repository

import net.corda.libs.packaging.Cpk
import net.corda.libs.packaging.core.CpiIdentifier
import net.corda.libs.packaging.core.CpiMetadata
import net.corda.v5.crypto.SecureHash
import java.util.stream.Stream
import javax.persistence.EntityManager
import javax.persistence.LockModeType

interface CpiMetadataRepository {

    /**
     * @return null if not found
     */
    fun findById(em: EntityManager, cpiId: CpiIdentifier): CpiMetadata?

    /**
     * @return null if not found
     */
    fun findById(em: EntityManager, cpiId: CpiIdentifier, lockMode: LockModeType): CpiMetadata?

    /**
     * @return null if not found
     */
    fun findByNameAndSignerSummaryHash(
        em: EntityManager,
        name: String,
        signerSummaryHash: SecureHash
    ): List<CpiMetadata>

    fun findByNameAndVersion(
        em: EntityManager,
        name: String,
        version: String
    ): CpiMetadata

    fun findByFileChecksum(em: EntityManager, cpiFileChecksum: String): CpiMetadata?

    fun findAll(em: EntityManager): Stream<Triple<Int, Boolean, CpiMetadata>>

    fun exist(em: EntityManager, cpiId: CpiIdentifier, lockMode: LockModeType): Boolean

    @Suppress("LongParameterList")
    fun put(
        em: EntityManager,
        cpiId: CpiIdentifier,
        cpiFileName: String,
        fileChecksum: SecureHash,
        groupId: String,
        groupPolicy: String,
        fileUploadRequestId: String,
        cpks: Collection<Cpk>
    )

    @Suppress("LongParameterList")
    fun update(
        em: EntityManager,
        cpiId: CpiIdentifier,
        cpiFileName: String,
        fileChecksum: SecureHash,
        groupId: String,
        groupPolicy: String,
        fileUploadRequestId: String,
        cpks: Collection<Cpk>,
        entityVersion: Int = 0
    ): CpiMetadata
}
