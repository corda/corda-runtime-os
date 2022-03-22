package net.corda.chunking.db.impl.persistence

import net.corda.chunking.RequestId
import net.corda.chunking.db.impl.AllChunksReceived
import net.corda.data.chunking.Chunk
import net.corda.packaging.CPI
import net.corda.v5.crypto.SecureHash

interface ChunkPersistence {
    /**
     * Persist a chunk and check whether we have all chunks,
     * and return [AllChunksReceived.YES] or `NO`.
     *
     * This occurs in a transaction so that only one process should "raise" one
     * final message to the rest of the system to validate the complete binary.
     *
     * @return returns [AllChunksReceived.YES] if all chunks received or [AllChunksReceived.NO]
     */
    fun persistChunk(chunk: Chunk): AllChunksReceived

    /**
     * Validates that the chunks written match the complete file uploaded.
     *
     * Does the checksum of the chunks in the persistence layer for this [requestId]
     * match the checksum for the [requestId] that has already been written to the
     * persistence layer?
     *
     * Assumes that all chunks have been received for the given [requestId]
     *
     * @return returns true if the checksum of the chunks matches the checksum already written
     * into the persistence layer
     */
    fun checksumIsValid(requestId: RequestId): Boolean

    /**
     * Gets chunks (if any) for a given [requestId] from the persistence layer
     * and calls [onChunk] for each chunk that is returned.
     *
     * @param requestId the requestId of the chunks
     * @param onChunk lambda method to be called on each chunk
     */
    fun forEachChunk(requestId: RequestId, onChunk: (chunk: Chunk) -> Unit)

    /**
     * Check if we already have a cpk persisted with this checksum
     *
     * @return true if checksum exists in the persistence layer
     */
    fun cpkExists(cpkChecksum: SecureHash): Boolean

    /** Checks to see if the CPI exists in the database using the primary key
     *
     * @return true if CPI exists
     */
    fun cpiExists(cpiName: String, cpiVersion: String, signerSummaryHash: String): Boolean

    /** Persist the CPI metadata and the CPKs
     *
     * @param cpi a [CPI] object
     * @param cpiFileName the original CPI file name
     * @param checksum the checksum of the CPI file
     * @param requestId the request id for the CPI that is being uploaded
     * @param groupId the group id from the group policy file
     */
    fun persistMetadataAndCpks(
        cpi: CPI,
        cpiFileName: String,
        checksum: SecureHash,
        requestId: RequestId,
        groupId: String
    )

    /** Get the group id for a given CPI */
    fun getGroupId(cpiName: String, cpiVersion: String, signerSummaryHash: String): String?
}
