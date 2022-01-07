package net.corda.crypto.impl.persistence

import net.corda.v5.cipher.suite.WrappedKeyPair
import net.corda.v5.cipher.suite.schemes.SignatureScheme
import java.security.PublicKey
import java.util.UUID

interface SigningKeyCache {
    fun save(info: PublicKeyInfo)
    fun save(wrappedKeyPair: WrappedKeyPair, masterKeyAlias: String, scheme: SignatureScheme, externalId: UUID?)
    fun find(publicKey: PublicKey): SigningPersistentKeyInfo?
    fun find(alias: String): SigningPersistentKeyInfo?
}

class PublicKeyInfo(
    val publicKey: PublicKey,
    val scheme: SignatureScheme,
    val category: String,
    val alias: String,
    val hsmAlias: String
)