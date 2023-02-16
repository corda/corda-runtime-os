package net.corda.cpk.write.impl.services.db

import net.corda.libs.cpi.datamodel.CpkFile
import net.corda.v5.crypto.SecureHash

interface CpkStorage {
    fun getCpkIdsNotIn(fileChecksums: List<SecureHash>): List<SecureHash>

    fun getCpkFileById(fileChecksum: SecureHash): CpkFile
}