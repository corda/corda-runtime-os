package net.corda.crypto.impl.persistence

import net.corda.crypto.impl.closeGracefully
import net.corda.v5.base.types.toHexString
import net.corda.v5.cipher.suite.KeyEncodingService
import net.corda.v5.cipher.suite.WrappedKeyPair
import net.corda.v5.cipher.suite.schemes.SignatureScheme
import net.corda.v5.crypto.sha256Bytes
import java.security.PublicKey
import java.util.UUID

class SigningKeyCacheImpl(
    private val memberId: String,
    private val keyEncoder: KeyEncodingService,
    persistenceFactory: KeyValuePersistenceFactory
) : SigningKeyCache, AutoCloseable {

    init {
        require(memberId.isNotBlank()) { "The member id must not be blank." }
    }

    val persistence: KeyValuePersistence<SigningPersistentKeyInfo, SigningPersistentKeyInfo> =
        persistenceFactory.createSigningPersistence(memberId, ::mutate)

    override fun find(publicKey: PublicKey): SigningPersistentKeyInfo? =
        persistence.get(toEntityKey(publicKey))?.clone()?.also {
            it.alias = fromEffectiveAlias(it.alias)
        }

    override fun find(alias: String): SigningPersistentKeyInfo? =
        persistence.get(effectiveAlias(alias))?.clone()?.also {
            it.alias = fromEffectiveAlias(it.alias)
        }

    override fun save(info: PublicKeyInfo ) {
        val key = toEntityKey(info.publicKey)
        val computedAlias = effectiveAlias(info.alias)
        val entity = SigningPersistentKeyInfo(
            tenantId = memberId,
            publicKeyHash = key,
            externalId = null,
            publicKey = keyEncoder.encodeAsByteArray(info.publicKey),
            category = info.category,
            alias = computedAlias,
            hsmAlias = info.hsmAlias,
            masterKeyAlias = null,
            privateKeyMaterial = null,
            schemeCodeName = info.scheme.codeName,
            version = 1
        )
        persistence.put(key, entity)
        persistence.put(computedAlias, entity)
    }

    override fun save(
        wrappedKeyPair: WrappedKeyPair,
        masterKeyAlias: String,
        scheme: SignatureScheme,
        externalId: UUID?
    ) {
        val publicKey = wrappedKeyPair.publicKey
        val keyHash = toEntityKey(publicKey)
        val entity = SigningPersistentKeyInfo(
            tenantId = memberId,
            publicKeyHash = keyHash,
            externalId = externalId,
            publicKey = keyEncoder.encodeAsByteArray(publicKey),
            alias = null,
            masterKeyAlias = masterKeyAlias,
            privateKeyMaterial = wrappedKeyPair.keyMaterial,
            schemeCodeName = scheme.codeName,
            version = 1
        )
        persistence.put(keyHash, entity)
    }

    private fun mutate(entity: SigningPersistentKeyInfo): SigningPersistentKeyInfo = entity

    private fun toEntityKey(publicKey: PublicKey): String =
        "$memberId:${publicKey.sha256Bytes().toHexString()}"

    private fun effectiveAlias(alias: String) =
        "$memberId:$alias"

    private fun fromEffectiveAlias(alias: String?) = alias?.removePrefix("$memberId:")

    override fun close() {
        (persistence as? AutoCloseable)?.closeGracefully()
    }
}