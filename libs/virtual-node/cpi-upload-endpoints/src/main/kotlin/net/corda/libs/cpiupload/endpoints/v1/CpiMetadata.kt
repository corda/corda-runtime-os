package net.corda.libs.cpiupload.endpoints.v1

import java.time.Instant

/**
 * This class is visible to end-users via json serialization,
 * so the variable names are significant, and should be consistent
 * with anywhere they are used as parameter inputs, e.g.`cpiFileChecksum`
 */
data class CpiMetadata(
    val id : CpiIdentifier,
    val cpiFileChecksum : String,
    val cpiFileFullChecksum : String,
    val cpks : List<CpkMetadata>,
    val groupPolicy : String?,
    val timestamp: Instant
)
