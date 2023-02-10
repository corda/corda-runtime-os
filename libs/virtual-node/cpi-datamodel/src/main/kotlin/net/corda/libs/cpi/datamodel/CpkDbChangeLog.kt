package net.corda.libs.cpi.datamodel

// Todo: Should this class remain inside this package or should it be moved to net.corda.libs.packaging.core? CpiMetadata, CpkMetadata are also in the latter packege
// Question above should be answered during the PR review
data class CpkDbChangeLog(
    val filePath: String,
    val content: String,
    val fileChecksum: String
)
