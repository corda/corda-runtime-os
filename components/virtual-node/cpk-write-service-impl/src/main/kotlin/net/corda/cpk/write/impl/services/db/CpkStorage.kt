package net.corda.cpk.write.impl.services.db

import net.corda.v5.crypto.SecureHash

interface CpkStorage {
    fun getCpkIdsNotIn(checksums: List<SecureHash>): List<SecureHash>

    fun getCpkDataByCpkId(checksum: SecureHash): CpkChecksumToData
}