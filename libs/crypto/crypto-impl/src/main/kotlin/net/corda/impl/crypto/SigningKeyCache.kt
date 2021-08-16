package net.corda.impl.crypto

import net.corda.v5.cipher.suite.schemes.SignatureScheme
import net.corda.v5.cipher.suite.WrappedKeyPair
import java.security.PublicKey
import java.util.*

interface SigningKeyCache {
    fun save(publicKey: PublicKey, scheme: SignatureScheme, alias: String)
    fun save(wrappedKeyPair: WrappedKeyPair, masterKeyAlias: String, scheme: SignatureScheme, externalId: UUID?)
    fun find(publicKey: PublicKey): SigningPersistentKey?
    fun find(alias: String): SigningPersistentKey?
}