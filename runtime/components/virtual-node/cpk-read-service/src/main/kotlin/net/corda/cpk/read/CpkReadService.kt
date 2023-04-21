package net.corda.cpk.read

import net.corda.lifecycle.Lifecycle
import net.corda.libs.packaging.Cpk
import net.corda.v5.crypto.SecureHash

interface CpkReadService : Lifecycle {
    fun get(cpkFileChecksum: SecureHash): Cpk?
}
