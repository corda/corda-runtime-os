package net.corda.crypto.core.aes.ecdh


// Avro
class ECDHAgreementParams(
    val salt: ByteArray,
    val digestName: String,
    val length: Int
)