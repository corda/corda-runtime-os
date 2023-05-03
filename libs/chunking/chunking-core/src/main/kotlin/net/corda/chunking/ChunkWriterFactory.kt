package net.corda.chunking

import net.corda.chunking.Constants.Companion.MAX_DB_CHUNK_SIZE
import net.corda.chunking.impl.ChunkBuilderServiceImpl
import net.corda.chunking.impl.ChunkWriterImpl
import net.corda.v5.base.exceptions.CordaRuntimeException

object ChunkWriterFactory {

    /** Note that the [maxAllowedMessageSize] (in bytes) *must* fit within a Kafka message */
    fun create(maxAllowedMessageSize: Int, properties: Map<String, String?>? = null): ChunkWriter {
        if (maxAllowedMessageSize > MAX_DB_CHUNK_SIZE) {
            throw CordaRuntimeException("Cannot write chunks larger than $MAX_DB_CHUNK_SIZE because it will exceed the db table definition")
        }

        return ChunkWriterImpl(maxAllowedMessageSize, ChunkBuilderServiceImpl(), properties)
    }
}
