package net.corda.sandbox.internal.utilities

import net.corda.packaging.Cpk
import net.corda.sandbox.internal.HASH_ALGORITHM
import net.corda.v5.crypto.SecureHash
import java.security.MessageDigest

/** Calculates a summary hash of the hashes of the public keys that signed the [cpk]. */
internal fun calculateCpkSignerSummaryHash(cpk: Cpk): SecureHash {
    val cpkSignerBytes = cpk.id.signers.sorted().joinToString("").toByteArray()
    val digest = MessageDigest.getInstance(HASH_ALGORITHM)
    digest.update(cpkSignerBytes)
    return SecureHash(digest.algorithm, digest.digest())
}