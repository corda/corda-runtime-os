package net.corda.crypto.softhsm

import java.security.PrivateKey
import java.security.PublicKey
import net.corda.crypto.cipher.suite.GeneratedWrappedKey

interface PrivateKeyService {
    fun wrap(alias: String, privateKey: PrivateKey): ByteArray

    fun store(wrappedKey: GeneratedWrappedKey)
    fun fetchFor(publicKey: PublicKey): GeneratedWrappedKey?
}
