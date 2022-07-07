package net.corda.crypto.core.aes.ecdh.handshakes

// Avro
class ReplyHandshake(
    val ephemeralPublicKey: ByteArray,
    val signature: ByteArray
)

