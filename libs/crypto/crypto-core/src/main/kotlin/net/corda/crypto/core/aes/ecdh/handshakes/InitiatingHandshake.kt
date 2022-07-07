package net.corda.crypto.core.aes.ecdh.handshakes

import net.corda.crypto.core.aes.ecdh.ECDHAgreementParams
import java.security.PublicKey

// Avro
class InitiatingHandshake(
    val params: ECDHAgreementParams,
    val ephemeralPublicKey: ByteArray
)