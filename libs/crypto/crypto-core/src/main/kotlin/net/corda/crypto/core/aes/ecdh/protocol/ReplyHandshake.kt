package net.corda.crypto.core.aes.ecdh.protocol

// Avro
class ReplyHandshake(
    val ephemeralPublicKey: ByteArray,
    val signature: ByteArray
)

