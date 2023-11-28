package net.corda.libs.cpiupload.endpoints.v1

import java.time.Instant

/**
 * NOTE:
 * This class is visible to end-users via JSON serialization,
 * so the variable names are significant, and should be consistent
 * anywhere they are used as parameter inputs, for example, `cpiFileChecksum`.
 */
/**
 * CPI info
 *
 * @param id CPI identifier.
 * @param cpiFileChecksum File Checksum for the CPI (shortened).
 * @param cpiFileFullChecksum File Checksum for the CPI (full).
 * @param cpks List of CPKs that are part of the CPI.
 * @param groupPolicy Contents of the CPI group policy file.
 * @param timestamp Timestamp indicating when the CPI was uploaded.
 */
data class CpiMetadata(
    val id: CpiIdentifier,
    val cpiFileChecksum: String,
    val cpiFileFullChecksum: String,
    val cpks: List<CpkMetadata>,
    val groupPolicy: String?,
    val timestamp: Instant
)
