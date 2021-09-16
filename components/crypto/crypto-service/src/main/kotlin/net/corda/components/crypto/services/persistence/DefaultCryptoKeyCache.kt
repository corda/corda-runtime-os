package net.corda.components.crypto.services.persistence

import net.corda.v5.cipher.suite.schemes.SignatureScheme
import java.security.KeyPair

interface DefaultCryptoKeyCache {
    fun save(alias: String, keyPair: KeyPair, scheme: SignatureScheme)
    fun save(alias: String, key: WrappingKey)
    fun find(alias: String): DefaultCryptoCachedKeyInfo?
}