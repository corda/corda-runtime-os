package net.corda.crypto.ecdh

import java.security.PublicKey

class EncryptedDataWithKey(
    val publicKey: PublicKey,
    val cipherText: ByteArray
)