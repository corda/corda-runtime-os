package net.corda.libs.cpi.datamodel

import net.corda.v5.crypto.SecureHash

data class CpiCpkIdentifier(val cpiName: String, val cpiVersion: String, val cpiSignerSummaryHash: SecureHash, val cpkFileChecksum: SecureHash)

