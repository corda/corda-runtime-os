package net.corda.crypto.core.aes.ecdh.protocol

// Avro
class InitiatingHandshake(
    val params: ByteArray,
    val ephemeralPublicKey: ByteArray
)