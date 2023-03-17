package net.cordapp.testing.packagingverification.contract

import net.corda.v5.application.crypto.DigestService
import net.corda.v5.base.types.MemberX500Name
import net.corda.v5.crypto.DigestAlgorithmName
import net.corda.v5.crypto.SecureHash

val ISSUER = MemberX500Name.parse("C=GB, L=London, O=Bob")
const val STATE_SYMBOL = "symbol"
val STATE_NAME = SimpleState::class.java.name

fun MemberX500Name.toSecureHash(digestService: DigestService): SecureHash {
    return digestService.hash(toString().toByteArray(), DigestAlgorithmName.SHA2_256)
}
