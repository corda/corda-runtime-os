package net.corda.chunking

import java.nio.ByteBuffer
import net.corda.data.KeyValuePairList
import net.corda.data.chunking.Chunk
import net.corda.v5.crypto.SecureHash

/**
 * Utility service to build chunks
 */
@Suppress("LongParameterList")
interface ChunkBuilderService {

    /**
     * Build the final `tombstone` chunk which is appended to the end of a set of chunks to signify this is the last chunk.
     * This chunk also includes a checksum to validate the data received
     * @param identifier id for grouping chunks
     * @param chunkNumber id for this chunk in the set of chunks
     * @param checksum checksum for the full bytes received in the previous chunks
     * @param offset offset for data
     * @param properties properties associated with this chunk
     * @param fileName file name
     */
    fun buildFinalChunk(
        identifier: String,
        chunkNumber: Int,
        checksum: SecureHash,
        offset: Long? = null,
        properties: KeyValuePairList? = null,
        //TODO - remove these CORE-9481
        fileName: String? = null,
    ) : Chunk

    /**
     * Build a chunk object to wrap some bytes that need to be sent in chunks
     * @param identifier id for grouping chunks
     * @param chunkNumber id for this chunk in the set of chunks
     * @param byteBuffer the bytes to send as part of this chunk
     * @param offset offset for data
     * @param properties properties associated with this chunk
     * @param fileName file name
     */
    fun buildChunk(
        identifier: String,
        chunkNumber: Int,
        byteBuffer: ByteBuffer,
        offset: Long? = null,
        properties: KeyValuePairList? = null,
        //TODO - remove these CORE-9481
        fileName: String? = null,
    ) : Chunk
}
