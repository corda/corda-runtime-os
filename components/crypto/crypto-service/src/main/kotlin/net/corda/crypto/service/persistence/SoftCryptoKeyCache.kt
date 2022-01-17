package net.corda.crypto.service.persistence

import net.corda.crypto.component.persistence.SoftKeysRecordInfo
import net.corda.crypto.component.persistence.WrappingKey
import net.corda.v5.cipher.suite.schemes.SignatureScheme
import java.security.KeyPair

interface SoftCryptoKeyCache {
    fun save(alias: String, keyPair: KeyPair, scheme: SignatureScheme)
    fun save(alias: String, key: WrappingKey)
    fun find(alias: String): SoftKeysRecordInfo?
}