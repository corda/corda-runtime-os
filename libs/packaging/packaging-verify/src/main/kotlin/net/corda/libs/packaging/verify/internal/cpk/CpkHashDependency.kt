package net.corda.libs.packaging.verify.internal.cpk

import net.corda.v5.crypto.SecureHash

/** CPK dependency matched by file hash */
internal data class CpkHashDependency (
    override val name: String,
    override val version: String,
    val fileHash: SecureHash
): CpkDependency {
    override fun satisfied(cpks: List<AvailableCpk>): Boolean =
        cpks.any {
            it.name ==  name &&
            it.version == version &&
            it.fileHashCalculator.hash(fileHash.algorithm) == fileHash
        }
}