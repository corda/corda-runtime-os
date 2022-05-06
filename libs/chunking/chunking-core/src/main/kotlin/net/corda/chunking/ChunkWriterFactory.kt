package net.corda.chunking

import net.corda.chunking.impl.ChunkWriterImpl
import net.corda.v5.base.exceptions.CordaRuntimeException

object ChunkWriterFactory {
    private const val KB = 1024
    private const val MB = 1024 * KB

    // This value should match the liquibase table value in `corda-api`
    private const val MAX_CHUNK_SIZE = 8 * MB

    // Half the minimum Kafka message size, which may contain other fields.
    const val SUGGESTED_CHUNK_SIZE = 512 * KB

    /** Note that the [chunkSize] (in bytes) *must* fit within a Kafka message */
    fun create(chunkSize: Int, forceUpload: Boolean): ChunkWriter {
        if (chunkSize > MAX_CHUNK_SIZE) {
            throw CordaRuntimeException("Cannot write chunks larger than $MAX_CHUNK_SIZE because it will exceed the db table definition")
        }

        return ChunkWriterImpl(chunkSize, forceUpload)
    }
}
