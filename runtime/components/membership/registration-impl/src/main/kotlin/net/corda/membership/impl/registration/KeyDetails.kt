package net.corda.membership.impl.registration

import net.corda.crypto.cipher.suite.PublicKeyHash
import net.corda.v5.crypto.SignatureSpec

internal interface KeyDetails {
    val pem: String
    val hash: PublicKeyHash
    val spec: SignatureSpec
}