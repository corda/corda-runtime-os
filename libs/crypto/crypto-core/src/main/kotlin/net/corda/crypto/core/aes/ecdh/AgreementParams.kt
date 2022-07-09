package net.corda.crypto.core.aes.ecdh

class AgreementParams(
    val salt: ByteArray,
    val digestName: String
)