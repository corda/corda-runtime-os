package net.corda.packaging.internal

import net.corda.v5.crypto.DigestAlgorithmName
import net.corda.v5.crypto.SecureHash
import java.security.MessageDigest

fun ByteArray.hash(algo : DigestAlgorithmName = DigestAlgorithmName.SHA2_256) : SecureHash {
    val md = MessageDigest.getInstance(algo.name)
    md.update(this)
    return SecureHash(algo.name, md.digest())
}