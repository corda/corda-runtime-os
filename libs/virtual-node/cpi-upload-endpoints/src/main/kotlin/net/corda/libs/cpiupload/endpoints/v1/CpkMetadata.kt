package net.corda.libs.cpiupload.endpoints.v1

import java.time.Instant

data class CpkMetadata(
    val id : CpkIdentifier,
    val mainBundle : String,
    val libraries : List<String>,
    val dependencies : List<CpkIdentifier>,
    val type : String,
    val hash: String,
    val timestamp: Instant
)