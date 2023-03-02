package net.corda.membership.lib.notary

import net.corda.crypto.cipher.suite.PublicKeyHash
import net.corda.v5.crypto.SignatureSpec
import java.security.PublicKey

data class MemberNotaryKey(
    val publicKey: PublicKey,
    val publicKeyHash: PublicKeyHash,
    val spec: SignatureSpec,
)
