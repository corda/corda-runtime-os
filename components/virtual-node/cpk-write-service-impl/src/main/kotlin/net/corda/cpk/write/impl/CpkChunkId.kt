package net.corda.cpk.write.impl

import net.corda.v5.crypto.SecureHash

data class CpkChunkId(
    val cpkChecksum: SecureHash,
    val partNumber: Int
)