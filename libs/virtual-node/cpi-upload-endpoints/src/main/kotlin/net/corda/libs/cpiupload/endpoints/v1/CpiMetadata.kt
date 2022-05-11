package net.corda.libs.cpiupload.endpoints.v1

import java.time.Instant

data class CpiMetadata(
    val id : CpiIdentifier,
    val fileChecksum : String,
    val cpks : List<CpkMetadata>,
    val groupPolicy : String?,
    val timestamp: Instant
)