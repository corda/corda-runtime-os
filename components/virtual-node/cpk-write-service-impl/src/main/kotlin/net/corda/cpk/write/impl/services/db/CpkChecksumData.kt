package net.corda.cpk.write.impl.services.db

import net.corda.v5.crypto.SecureHash

@Suppress("warnings")
data class CpkChecksumData(
    val checksum: SecureHash,
    val bytes: ByteArray
)