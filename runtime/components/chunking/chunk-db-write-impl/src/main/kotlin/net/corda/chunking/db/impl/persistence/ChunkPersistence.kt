package net.corda.chunking.db.impl.persistence

import net.corda.chunking.RequestId
import net.corda.chunking.db.impl.AllChunksReceived
import net.corda.data.chunking.Chunk

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
}
