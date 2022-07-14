package net.corda.crypto.ecies

import java.security.PublicKey

class EncryptedDataWithKey(
    val publicKey: PublicKey,
    val cipherText: ByteArray
)