package net.corda.membership.lib.interop

import net.corda.v5.crypto.PublicKeyHash
import net.corda.v5.crypto.SignatureSpec
import java.security.PublicKey

data class MemberInteropKey(
    val publicKey: PublicKey,
    val publicKeyHash: PublicKeyHash,
    val spec: SignatureSpec,
)