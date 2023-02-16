package net.corda.libs.cpi.datamodel

import net.corda.v5.crypto.SecureHash

data class CpkChangeLogIdentifier(val cpkFileChecksum: SecureHash, val filePath: String)