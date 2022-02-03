package net.corda.crypto.service.impl.persistence

import net.corda.crypto.persistence.CachedSoftKeysRecord
import net.corda.crypto.persistence.WrappingKey
import net.corda.v5.cipher.suite.schemes.SignatureScheme
import java.security.KeyPair

interface SoftCryptoKeyCache {
    fun save(alias: String, keyPair: KeyPair, scheme: SignatureScheme)
    fun save(alias: String, key: WrappingKey)
    fun find(alias: String): CachedSoftKeysRecord?
}