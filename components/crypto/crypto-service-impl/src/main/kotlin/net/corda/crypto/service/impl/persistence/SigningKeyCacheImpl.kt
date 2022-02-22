package net.corda.crypto.service.impl.persistence

import net.corda.crypto.core.CryptoConsts
import net.corda.crypto.persistence.EntityKeyInfo
import net.corda.crypto.persistence.KeyValuePersistence
import net.corda.crypto.persistence.SigningKeysPersistenceProvider
import net.corda.data.crypto.persistence.SigningKeysRecord
import net.corda.v5.cipher.suite.KeyEncodingService
import net.corda.v5.cipher.suite.WrappedKeyPair
import net.corda.v5.cipher.suite.schemes.SignatureScheme
import net.corda.v5.crypto.calculateHash
import java.nio.ByteBuffer
import java.security.PublicKey
import java.time.Instant
import java.util.UUID

class SigningKeyCacheImpl(
    private val tenantId: String,
    private val keyEncoder: KeyEncodingService,
    persistenceFactory: SigningKeysPersistenceProvider
) : SigningKeyCache, AutoCloseable {

    init {
        require(tenantId.isNotBlank()) { "The member id must not be blank." }
    }

    val persistence: KeyValuePersistence<SigningKeysRecord, SigningKeysRecord> =
        persistenceFactory.getInstance(tenantId, ::mutate)

    override fun find(publicKey: PublicKey): SigningKeysRecord? {
        val entity = persistence.get(toKeyDerivedId(publicKey))
        return if(entity != null && entity.tenantId != tenantId) {
            null
        } else {
            entity
        }
    }

    override fun find(alias: String): SigningKeysRecord? {
        val entity = persistence.get(toAliasDerivedId(alias))
        return if(entity != null && entity.tenantId != tenantId) {
            null
        } else {
            entity
        }
    }

    override fun save(
        publicKey: PublicKey,
        scheme: SignatureScheme,
        category: String,
        alias: String,
        hsmAlias: String
    ) {
        val entity = SigningKeysRecord(
            tenantId,
            category,
            alias,
            hsmAlias,
            ByteBuffer.wrap(keyEncoder.encodeAsByteArray(publicKey)),
            null,
            scheme.codeName,
            null,
            null,
            1,
            Instant.now()
        )
        persistence.put(
            entity,
            EntityKeyInfo(EntityKeyInfo.PUBLIC_KEY, toKeyDerivedId(publicKey)),
            EntityKeyInfo(EntityKeyInfo.ALIAS, toAliasDerivedId(alias))
        )
    }

    override fun save(
        wrappedKeyPair: WrappedKeyPair,
        masterKeyAlias: String,
        scheme: SignatureScheme,
        externalId: UUID?
    ) {
        val entity = SigningKeysRecord(
            tenantId,
            CryptoConsts.Categories.FRESH_KEYS,
            null,
            null,
            ByteBuffer.wrap(keyEncoder.encodeAsByteArray(wrappedKeyPair.publicKey)),
            ByteBuffer.wrap(wrappedKeyPair.keyMaterial),
            scheme.codeName,
            masterKeyAlias,
            externalId?.toString(),
            1,
            Instant.now()
        )
        persistence.put(
            entity,
            EntityKeyInfo(EntityKeyInfo.PUBLIC_KEY, toKeyDerivedId(wrappedKeyPair.publicKey))
        )
    }

    private fun mutate(entity: SigningKeysRecord): SigningKeysRecord = entity

    private fun toKeyDerivedId(publicKey: PublicKey): String =
        "$tenantId:${publicKey.calculateHash()}"

    private fun toAliasDerivedId(alias: String) =
        "$tenantId:$alias"

    override fun close() {
        (persistence as? AutoCloseable)?.close()
    }
}