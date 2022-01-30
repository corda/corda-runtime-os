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
     * Called when a complete set of chunks have been received.
     *
     * @throws CordaRuntimeException if on complete callback is already set.
     */
    fun onComplete(chunksCombinedCallback: ChunksCombined)
}
