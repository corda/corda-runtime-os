package net.corda.messaging.api.chunking

import net.corda.data.chunking.Chunk
import net.corda.data.chunking.ChunkKey

/**
 * Service to handle assembling chunks into their original values.
 */
interface ConsumerChunkDeserializerService<K : Any, V: Any> : ChunkDeserializerService<V> {

    /**
     * Take a list of [Chunk]s mapped by [ChunkKey]s, validate the checksum and deserialize the bytes.
     * @param chunks Chunks to assemble
     * @return The deserialized key bytes and value bytes. Returns null if deserialization fails or if the checksum does not match.
     */
    fun assembleChunks(chunks: Map<ChunkKey, Chunk>): Pair<K, V?>?
}
