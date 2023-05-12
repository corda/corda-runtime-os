package net.corda.membership.lib.notary

import net.corda.v5.crypto.SecureHash
import net.corda.v5.crypto.SignatureSpec
import java.security.PublicKey

data class MemberNotaryKey(
    val publicKey: PublicKey,
    val publicKeyHash: SecureHash,
    val spec: SignatureSpec,
)
