package net.corda.libs.cpi.datamodel

import net.corda.libs.packaging.core.CpkFormatVersion
import net.corda.libs.packaging.core.CpkIdentifier
import net.corda.v5.crypto.SecureHash

data class CpkMetadataLite(val cpkId: CpkIdentifier,
    val cpkFileChecksum: SecureHash,
    val cpkFormatVersion: CpkFormatVersion,
    var serializedMetadata: String // JsonAvro format
)