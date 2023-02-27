package net.corda.chunking

class Constants {
    companion object {
        const val KB = 1024
        const val MB = 1024 * KB
        //Additional overhead when checking max size of a record
        const val CORDA_RECORD_OVERHEAD = 10 * KB
        //Additional overhead when checking max size of a chunk. This allows extra overhead to avoid message bus chunking
        const val APP_LEVEL_CHUNK_MESSAGE_OVERHEAD = CORDA_RECORD_OVERHEAD * 2
        // This value should match the liquibase table value in `corda-api`
        const val MAX_DB_CHUNK_SIZE = 8 * MB
        const val SECURE_HASH_VALIDATION_ERROR = "Checksums do not match, one or more of the chunks may be corrupt"
        const val SECURE_HASH_MISSING_ERROR = "Checksum is missing from the final chunk"
    }
}
