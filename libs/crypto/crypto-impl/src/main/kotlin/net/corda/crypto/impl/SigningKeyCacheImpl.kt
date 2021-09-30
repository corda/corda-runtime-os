package net.corda.crypto.impl

import net.corda.v5.base.types.toHexString
import net.corda.v5.cipher.suite.KeyEncodingService
import net.corda.v5.cipher.suite.WrappedKeyPair
import net.corda.v5.cipher.suite.schemes.SignatureScheme
import net.corda.v5.crypto.sha256Bytes
import java.security.PublicKey
import java.util.UUID

open class SigningKeyCacheImpl(
    private val sandboxId: String,
    private val keyEncoder: KeyEncodingService,
    private val cacheFactory: SigningServicePersistentCacheFactory
) : SigningKeyCache {
    constructor(
        sandboxId: String,
        keyEncoder: KeyEncodingService,
        sessionFactory: () -> Any
    ) : this(
        sandboxId,
        keyEncoder,
        SigningServicePersistentCacheFactoryImpl(sessionFactory)
    )

    private val cache: SigningServicePersistentCache by lazy {
        cacheFactory.create()
    }

    override fun find(publicKey: PublicKey): SigningPersistentKey? = cache.get(toEntityKey(publicKey))?.clone()?.also {
        it.alias = fromEffectiveAlias(it.alias)
    }

    override fun find(alias: String): SigningPersistentKey? = cache.findByAlias(effectiveAlias(alias))?.clone()?.also {
        it.alias = fromEffectiveAlias(it.alias)
    }

    override fun save(publicKey: PublicKey, scheme: SignatureScheme, alias: String) {
        val key = toEntityKey(publicKey)
        val entity = SigningPersistentKey(
            sandboxId = sandboxId,
            publicKeyHash = key,
            externalId = null,
            publicKey = keyEncoder.encodeAsByteArray(publicKey),
            alias = effectiveAlias(alias),
            masterKeyAlias = null,
            privateKeyMaterial = null,
            schemeCodeName = scheme.codeName,
            version = 1
        )
        cache.put(key, entity)
    }

    override fun save(
        wrappedKeyPair: WrappedKeyPair,
        masterKeyAlias: String,
        scheme: SignatureScheme,
        externalId: UUID?
    ) {
        val publicKey = wrappedKeyPair.publicKey
        val keyHash = toEntityKey(publicKey)
        val entity = SigningPersistentKey(
            sandboxId = sandboxId,
            publicKeyHash = keyHash,
            externalId = externalId,
            publicKey = keyEncoder.encodeAsByteArray(publicKey),
            alias = null,
            masterKeyAlias = masterKeyAlias,
            privateKeyMaterial = wrappedKeyPair.keyMaterial,
            schemeCodeName = scheme.codeName,
            version = 1
        )
        cache.put(keyHash, entity)
    }

    private fun toEntityKey(publicKey: PublicKey): String =
        publicKey.sha256Bytes().toHexString()

    private fun effectiveAlias(alias: String) = "$sandboxId:$alias"

    private fun fromEffectiveAlias(alias: String?) = alias?.removePrefix("$sandboxId:")
}