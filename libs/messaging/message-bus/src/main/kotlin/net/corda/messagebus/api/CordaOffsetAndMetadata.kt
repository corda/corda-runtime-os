package net.corda.messagebus.api

/**
 * Additional metadata (in the form of a string) supplied when an offset is committed.
 * This can be useful (for example) to store information about which
 * node made the commit, what time the commit was made, etc.
*/
data class CordaOffsetAndMetadata(
    val offset: Long,
    val metadata: String = "",
)
