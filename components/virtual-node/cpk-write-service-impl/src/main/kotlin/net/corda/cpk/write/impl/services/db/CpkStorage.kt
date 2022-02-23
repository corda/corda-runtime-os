package net.corda.cpk.write.impl.services.db

import net.corda.v5.crypto.SecureHash
import java.util.stream.Stream

interface CpkStorage {
    fun getCpkIdsNotIn(checksums: Set<SecureHash>): Set<SecureHash>

    fun getCpkBlobByCpkId(checksum: SecureHash): CpkChecksumData
}