package net.corda.chunking

import net.corda.chunking.impl.ChunkWriterImpl
import net.corda.v5.base.exceptions.CordaRuntimeException

object ChunkWriterFactory {
    private const val KB = 1024
    private const val MB = 1024 * KB

    // This value should match the liquibase table value in `corda-api`
    private const val MAX_CHUNK_SIZE = 8 * MB

    /** Note that the [maxAllowedMessageSize] (in bytes) *must* fit within a Kafka message */
    fun create(maxAllowedMessageSize: Int, properties: Map<String, String?>? = null): ChunkWriter {
        if (maxAllowedMessageSize > MAX_CHUNK_SIZE) {
            throw CordaRuntimeException("Cannot write chunks larger than $MAX_CHUNK_SIZE because it will exceed the db table definition")
        }

        return ChunkWriterImpl(maxAllowedMessageSize, properties)
    }
}
