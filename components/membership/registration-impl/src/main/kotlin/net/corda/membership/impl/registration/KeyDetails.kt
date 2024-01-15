package net.corda.membership.impl.registration

import net.corda.v5.crypto.SecureHash
import net.corda.v5.crypto.SignatureSpec

internal interface KeyDetails {
    val pem: String
    val hash: SecureHash
    val spec: SignatureSpec
}
