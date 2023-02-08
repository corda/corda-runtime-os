package net.corda.chunking

class Constants {
    companion object {
        const val KB = 1024
        const val MB = 1024 * KB
        const val CORDA_MESSAGE_OVERHEAD = 1024 * 10
        // This value should match the liquibase table value in `corda-api`
        const val MAX_DB_CHUNK_SIZE = 8 * MB
        const val SECURE_HASH_VALIDATION_ERROR = "Checksums do not match, one or more of the chunks may be corrupt"
        const val SECURE_HASH_MISSING_ERROR = "Checksum is missing from the final chunk"
    }
}
