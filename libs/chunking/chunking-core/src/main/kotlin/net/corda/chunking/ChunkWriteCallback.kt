package net.corda.chunking

import net.corda.data.chunking.Chunk

fun interface ChunkWriteCallback {
    /**
     * When an Avro [Chunk] has been created, this method is called and expected to do "the writing" of the chunk,
     * whatever that may be (e.g. publish to Kafka).
     */
    fun onChunk(chunk: Chunk)
}
