package net.corda.chunking.db.impl

import net.corda.chunking.Checksum
import net.corda.chunking.datamodel.ChunkEntity
import net.corda.chunking.toCorda
import net.corda.data.chunking.Chunk
import net.corda.orm.utils.transaction
import net.corda.v5.base.exceptions.CordaRuntimeException
import net.corda.v5.crypto.SecureHash
import javax.persistence.EntityManagerFactory

/**
 * This class provides some simple methods to put chunks and retrieve the status
 * of any given request.
 *
 * If all chunks are received, then we can also calculate its checksum.
 */
internal class ChunkDbQueries(private val entityManagerFactory: EntityManagerFactory) {

    /**
     * Persist a chunk to the database and also check whether we have all chunks,
     * and return [AllChunksReceived.YES] or `NO`.
     *
     * This occurs in a transaction so that only one process should "raise" one
     * final message to the rest of the system to validate the complete binary.
     */

    fun persist(chunk: Chunk): AllChunksReceived {
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
                data
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
            if (chunkCountIfComplete != 0) { status = AllChunksReceived.YES }
        }

        return status
    }


    /** Is the checksum for the given [requestId] valid? */
    fun checksumIsValid(requestId: String): Boolean {
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

            val messageDigest = Checksum.getMessageDigest()

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
}
