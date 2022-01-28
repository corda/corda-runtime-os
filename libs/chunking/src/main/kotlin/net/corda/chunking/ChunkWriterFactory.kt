package net.corda.chunking

import net.corda.chunking.impl.ChunkWriterImpl

object ChunkWriterFactory {
    /** Note that the [chunkSize] (in bytes) *must* fit within a Kafka message */
    fun create(chunkSize: Int): ChunkWriter = ChunkWriterImpl(chunkSize)
}
