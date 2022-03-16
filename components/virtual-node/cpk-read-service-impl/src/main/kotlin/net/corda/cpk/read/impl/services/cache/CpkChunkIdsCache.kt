package net.corda.cpk.read.impl.services.cache

import net.corda.data.chunking.CpkChunkId
import net.corda.v5.crypto.SecureHash
import java.util.SortedSet

internal interface CpkChunkIdsCache {
    /**
     * Gets cached chunk Ids for a CPK.
     */
    fun getChunkIds(cpkChecksum: SecureHash): SortedSet<CpkChunkId>?

    /**
     * Checks if all chunks are received for a CPK.
     */
    fun allChunksReceived(cpkChecksum: SecureHash): Boolean

    /**
     * Adds a new CPK chunk to cached CPK chunks or if the new CPK chunk [isZeroChunk] sets CPK chunks
     * expected count.
     */
    fun addOrSetExpected(cpkChecksum: SecureHash, chunkId: CpkChunkId, isZeroChunk: Boolean)

    /**
     * Removes chunks of the same CPK.
     * @return The removed cached [CpkChunkId]s of a CPK.
     */
    fun remove(cpkChecksum: SecureHash): Set<CpkChunkId>?

    /**
     * Default implementation.
     */
    class Impl : CpkChunkIdsCache {
        private val chunkIdsPerCpk = mutableMapOf<SecureHash, ChunksReceived>()

        override fun getChunkIds(cpkChecksum: SecureHash): SortedSet<CpkChunkId>? =
            chunkIdsPerCpk[cpkChecksum]?.chunks

        override fun allChunksReceived(cpkChecksum: SecureHash): Boolean =
            chunkIdsPerCpk[cpkChecksum]?.allReceived() ?: false

        override fun addOrSetExpected(cpkChecksum: SecureHash, chunkId: CpkChunkId, isZeroChunk: Boolean) {
            val chunksReceived = chunkIdsPerCpk[cpkChecksum]
                ?: ChunksReceived().also { chunkIdsPerCpk[cpkChecksum] = it }

            if (isZeroChunk) {
                chunksReceived.expectedCount = chunkId.cpkChunkPartNumber
            } else {
                chunksReceived.chunks.add(chunkId)
            }
        }

        override fun remove(cpkChecksum: SecureHash) =
            chunkIdsPerCpk.remove(cpkChecksum)?.chunks

        private class ChunksReceived {
            val chunks = sortedSetOf<CpkChunkId>()
            var expectedCount = -1
                set(value) {
                    if (field == -1) {
                        field = value
                    }
                }

            fun allReceived() =
                chunks.size == expectedCount
        }
    }
}