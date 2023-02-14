package net.corda.messaging.api.chunking

import net.corda.data.chunking.Chunk

/**
 * Service to handle assembling chunks into their original values.
 */
interface ChunkDeserializerService <V: Any> {

    /**
     * Take a list of chunks, validate the checksum and deserialize the bytes.
     * @return The deserialized bytes. Returns null if deserialization fails or if the checksum does not match.
     */
    fun assembleChunks(chunks: List<Chunk>): V?
}
