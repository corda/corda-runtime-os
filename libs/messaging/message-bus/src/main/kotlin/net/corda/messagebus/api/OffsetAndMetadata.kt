package net.corda.messagebus.api

data class OffsetAndMetadata(
    val offset: Long,
    val metadata: String = "",
)
