package net.corda.chunking

import net.corda.v5.base.exceptions.CordaRuntimeException
import java.io.InputStream
import java.nio.file.Path

interface ChunkWriter {
    /**
     * Break up an [InputStream] into chunks of some unspecified size (smaller than the default Kafka message size).
     * The given [fileName] will be returned via the [ChunkReader] and the [ChunksCombined] callback.
     */
    fun write(fileName: Path, inputStream: InputStream)

    /**
     * When a chunk is created, it is passed to the [ChunkWriteCallback], it is up to the implementer to write the
     * chunk to the appropriate destination, e.g. you'll publish this chunk on Kafka.
     *
     * This method is used to register a [ChunkWriteCallback] to the [ChunkWriter].
     *
     * The total number of times [ChunkWriteCallback.onChunk] method is called for a given binary is unknown until
     * the [InputStream] used by [write] is fully consumed, and the final zero-sized [Chunk] is written.
     *
     * @throws CordaRuntimeException if on chunk callback is already set.
     */
    fun onChunk(onChunkWriteCallback: ChunkWriteCallback)
}
