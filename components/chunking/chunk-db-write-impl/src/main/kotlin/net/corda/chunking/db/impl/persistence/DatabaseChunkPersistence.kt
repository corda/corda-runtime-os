package net.corda.chunking.db.impl.persistence

import net.corda.chunking.Checksum
import net.corda.chunking.RequestId
import net.corda.chunking.datamodel.ChunkEntity
import net.corda.chunking.db.impl.AllChunksReceived
import net.corda.chunking.toAvro
import net.corda.chunking.toCorda
import net.corda.data.chunking.Chunk
import net.corda.libs.cpi.datamodel.CpiMetadataEntity
import net.corda.libs.cpi.datamodel.CpiMetadataEntityKey
import net.corda.libs.cpi.datamodel.CpkDataEntity
import net.corda.libs.cpi.datamodel.CpkMetadataEntity
import net.corda.orm.utils.transaction
import net.corda.packaging.CPI
import net.corda.v5.base.exceptions.CordaRuntimeException
import net.corda.v5.crypto.SecureHash
import java.nio.ByteBuffer
import java.nio.file.Files
import javax.persistence.EntityManager
import javax.persistence.EntityManagerFactory

/**
 * This class provides some simple APIs to interact with the database.
 */
class DatabaseChunkPersistence(private val entityManagerFactory: EntityManagerFactory) : ChunkPersistence {

    /**
     * Persist a chunk to the database and also check whether we have all chunks,
     * and return [AllChunksReceived.YES] or `NO`.
     *
     * This occurs in a transaction so that only one process should "raise" one
     * final message to the rest of the system to validate the complete binary.
     */

    override fun persistChunk(chunk: Chunk): AllChunksReceived {
        // We put `null` into the data field if the array is empty, i.e. the final chunk
        // so that we can write easier queries for the parts expected and received.
        val data = if (chunk.data.array().isEmpty()) null else chunk.data.array()
        val cordaSecureHash = chunk.checksum?.toCorda()
        var status = AllChunksReceived.NO
        entityManagerFactory.createEntityManager().transaction {
            // Persist this chunk.
            val entity = ChunkEntity(
                chunk.requestId,
                chunk.fileName,
                cordaSecureHash?.toString(),
                chunk.partNumber,
                chunk.offset,
                data,
                chunk.forceUpload
            )
            it.persist(entity)

            // At this point we have at least one chunk.
            val table = ChunkEntity::class.simpleName
            val chunkCountIfComplete = it.createQuery(
                """
                SELECT 'hasAllChunks' FROM $table c1
                INNER JOIN $table c2 ON c1.requestId = c2.requestId AND c2.data IS NULL
                WHERE c1.requestId = :requestId AND c1.data IS NOT NULL
                GROUP BY c1.requestId, c2.partNumber
                HAVING COUNT(c1.requestId) = c2.partNumber
                """.trimIndent()
            )
                .setParameter("requestId", chunk.requestId)
                .resultList.count()

            // [chunkCountIfComplete] is either 0, i.e. incomplete, or some non-zero value
            // which is the total number chunks, if complete.
            if (chunkCountIfComplete != 0) {
                status = AllChunksReceived.YES
            }
        }

        return status
    }

    /**
     * Is the checksum for the given [requestId] valid?
     *
     * Assumes that all chunks have been received for the given [requestId]
     */
    override fun checksumIsValid(requestId: RequestId): Boolean {
        var expectedChecksum: SecureHash? = null
        var actualChecksum: ByteArray? = null

        entityManagerFactory.createEntityManager().transaction {
            val streamingResults = it.createQuery(
                "SELECT c FROM ${ChunkEntity::class.simpleName} c " +
                        "WHERE c.requestId = :requestId " +
                        "ORDER BY c.partNumber ASC",
                ChunkEntity::class.java
            )
                .setParameter("requestId", requestId)
                .resultStream

            val messageDigest = Checksum.newMessageDigest()

            streamingResults.forEach { e ->
                if (e.data == null) { // zero chunk
                    if (e.checksum == null) throw CordaRuntimeException("No checksum found in zero-sized chunk")
                    expectedChecksum = SecureHash.create(e.checksum!!)
                } else { // non-zero chunk
                    messageDigest.update(e.data!!)
                }
            }

            if (expectedChecksum == null) throw CordaRuntimeException("Expected checksum not set because no zero-sized chunk was found")

            actualChecksum = messageDigest.digest()
        }

        return expectedChecksum!!.bytes.contentEquals(actualChecksum)
    }

    /**
     * Gets chunks (if any) for a given [requestId] from database and calls [onChunk] for each chunk that is returned.
     *
     * @param requestId the requestId of the chunks
     * @param onChunk lambda method to be called on each chunk
     */
    override fun forEachChunk(requestId: RequestId, onChunk: (chunk: Chunk) -> Unit) {
        entityManagerFactory.createEntityManager().transaction {
            val table = ChunkEntity::class.simpleName
            val streamingResults = it.createQuery(
                """
                SELECT c FROM $table c
                WHERE c.requestId = :requestId
                ORDER BY c.requestId ASC
                """.trimIndent(),
                ChunkEntity::class.java
            )
                .setParameter("requestId", requestId)
                .resultStream

            streamingResults.forEach { entity ->
                // Do the reverse of [persist] particularly for data - if null, return zero bytes.
                val checksum = if (entity.checksum != null) SecureHash.create(entity.checksum!!).toAvro() else null
                val data = if (entity.data != null) ByteBuffer.wrap(entity.data) else ByteBuffer.allocate(0)
                val chunk = Chunk(requestId, entity.fileName, checksum, entity.partNumber, entity.offset, data,
                    entity.forceUpload)
                onChunk(chunk)
            }
        }
    }

    /**
     * Check if we already have a cpk persisted with this checksum
     *
     * @return true if checksum exists in database
     */
    override fun cpkExists(cpkChecksum: SecureHash): Boolean {
        val entity = entityManagerFactory.createEntityManager().transaction {
            it.find(CpkDataEntity::class.java, cpkChecksum.toString())
        }

        return entity != null
    }

    override fun cpiExists(cpiName: String, cpiVersion: String, signerSummaryHash: String): Boolean =
        getCpiEntity(cpiName, cpiVersion, signerSummaryHash) != null

    override fun persistMetadataAndCpks(
        cpi: CPI,
        cpiFileName: String,
        checksum: SecureHash,
        requestId: RequestId,
        groupId: String,
        overwrite: Boolean
    ) {
        val cpiMetadataEntity = createCpiMetadataEntity(cpi, cpiFileName, checksum, requestId, groupId)
        entityManagerFactory.createEntityManager().transaction { em ->
            if (overwrite) {
                em.deleteCpiMetadataEntity(cpiMetadataEntity)
            }
            em.persist(cpiMetadataEntity)
            cpi.cpks.forEach {
                val cpkChecksum = it.metadata.hash.toString()
                val cpkDataEntity = CpkDataEntity(cpkChecksum, Files.readAllBytes(it.path!!))
                if (overwrite) {
                    // em.deleteEntity(cpkDataEntity)
                } else {
                    em.persist(cpkDataEntity)
                }
                val cpkMetadataEntity = CpkMetadataEntity(cpiMetadataEntity, cpkChecksum, it.originalFileName!!)
                if (overwrite) {
                    // em.deleteEntity(cpkMetadataEntity)
                } else {
                    em.merge(cpkMetadataEntity)
                }
            }
        }
    }

    private fun EntityManager.deleteCpiMetadataEntity(cpiMetadataEntity: CpiMetadataEntity) {
        val prevEntity = merge(
            findCpiMetadataEntity(cpiMetadataEntity.name, cpiMetadataEntity.version, cpiMetadataEntity.signerSummaryHash))
        remove(prevEntity)
    }

    private fun EntityManager.findCpiMetadataEntity(
        name: String,
        version: String,
        signerSummaryHash: String
    ): CpiMetadataEntity? {
        val primaryKey = CpiMetadataEntityKey(
            name,
            version,
            signerSummaryHash
        )
        val entity = transaction {
            it.find(CpiMetadataEntity::class.java, primaryKey)
        }
        return entity
    }

    private fun getCpiEntity(name: String, version: String, signerSummaryHash: String): CpiMetadataEntity? {
        return entityManagerFactory.createEntityManager().findCpiMetadataEntity(name, version, signerSummaryHash)
    }

    override fun getGroupId(cpiName: String, cpiVersion: String, signerSummaryHash: String): String? {
        return getCpiEntity(cpiName, cpiVersion, signerSummaryHash)?.groupId
    }

    /**
     * For a given CPI create the metadata entity required to insert into the database.
     *
     * @param cpi CPI object
     * @param cpiFileName original file name
     * @param checksum checksum/hash of the CPI
     * @param requestId the requestId originating from the chunk upload
     */
    private fun createCpiMetadataEntity(
        cpi: CPI,
        cpiFileName: String,
        checksum: SecureHash,
        requestId: RequestId,
        groupId: String
    ): CpiMetadataEntity {
        val cpiMetadata = cpi.metadata

        return CpiMetadataEntity(
            cpiMetadata.id.name,
            cpiMetadata.id.version,
            cpiMetadata.id.signerSummaryHash?.toString() ?: "",
            cpiFileName,
            checksum.toString(),
            cpi.metadata.groupPolicy!!,
            groupId,
            requestId
        )
    }
}
