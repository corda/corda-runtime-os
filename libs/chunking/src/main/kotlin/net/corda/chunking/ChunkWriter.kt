package net.corda.chunking

import net.corda.v5.crypto.SecureHash
import java.io.InputStream

interface ChunkWriter {
    /**
     * Break up an [InputStream] into chunks of some unspecified size (smaller than the default Kafka message size).
     * The given [identifier] will be returned via the [ChunkWriter] and the [ChunksCombined] callback.  You
     * should use this to track the binary artifact between reader and writer.
     */
    fun write(identifier: SecureHash, inputStream: InputStream)

    /**
     * When a chunk is created, it is passed to the [ChunkWriteCallback], it is up to the implementer to write the
     * chunk to the appropriate destination, e.g. you'll publish this chunk on Kafka.
     *
     * The total number of times this method is called for a given identifier is unknown until the [InputStream] in
     * [write] is full read, and the final zero-sized [Chunk] is written.
     */
    fun onChunk(onChunkWriteCallback: ChunkWriteCallback)
}
