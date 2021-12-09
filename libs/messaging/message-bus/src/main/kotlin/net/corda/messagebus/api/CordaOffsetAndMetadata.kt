package net.corda.messagebus.api

data class CordaOffsetAndMetadata(
    val offset: Long,
    val metadata: String = "",
)
