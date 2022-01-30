package net.corda.chunking

import net.corda.data.chunking.Chunk
import net.corda.v5.base.exceptions.CordaRuntimeException

interface ChunkReader {
    /**
     * Read a chunk and attempt to recreate a full binary from this chunk and any
     * existing chunks.  When complete, the callback specified in [onComplete]
     * will be called.
     *
     * You will pass a chunk received from Kafka into this method.
     */
    fun read(chunk: Chunk)

    /**
     * This method is used to register a [ChunksCombined] callback to the [ChunkReader].
     *
     * [ChunksCombined.onChunksCombined] will be called when a complete set of chunks will have been received.
     *
     * @throws CordaRuntimeException if on complete callback is already set.
     */
    fun onComplete(chunksCombinedCallback: ChunksCombined)
}
