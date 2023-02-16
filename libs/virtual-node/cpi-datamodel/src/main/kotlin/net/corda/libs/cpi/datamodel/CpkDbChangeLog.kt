package net.corda.libs.cpi.datamodel

import net.corda.v5.crypto.SecureHash

// Todo: Should this class remain inside this package or should it be moved to net.corda.libs.packaging.core? CpiMetadata, CpkMetadata are also in the latter packege
// Question above should be answered during the PR review
data class CpkDbChangeLog(
    val filePath: String,
    val content: String,
    val fileChecksum: SecureHash
)
