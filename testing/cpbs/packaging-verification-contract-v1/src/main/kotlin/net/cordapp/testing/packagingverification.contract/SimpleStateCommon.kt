package net.cordapp.testing.packagingverification.contract

import net.corda.v5.base.types.MemberX500Name
import net.corda.v5.crypto.DigestAlgorithmName
import net.corda.v5.crypto.SecureHash
import java.security.MessageDigest

val ISSUER = MemberX500Name.parse("C=GB, L=London, O=Bob")
const val STATE_SYMBOL = "symbol"
val STATE_NAME = SimpleState::class.java.name

fun MemberX500Name.toSecureHash(): SecureHash {
    val algorithm = DigestAlgorithmName.SHA2_256.name
    return SecureHash(algorithm, MessageDigest.getInstance(algorithm).digest(toString().toByteArray()))
}
