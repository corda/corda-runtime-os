package net.corda.chunking

import net.corda.chunking.impl.ChunkWriterImpl
import net.corda.chunking.impl.ChunkWriterImpl.Companion.MB

object ChunkWriterFactory {
    /** Note that the chunk size *must* fit within a Kafka message */
    fun create() : ChunkWriter = ChunkWriterImpl(chunkSize = 1 * MB)
}
