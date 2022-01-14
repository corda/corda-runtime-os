package net.corda.crypto.service.persistence

import net.corda.crypto.CryptoConsts
import net.corda.crypto.component.persistence.KeyValuePersistenceFactory
import net.corda.crypto.component.persistence.SigningKeyRecord
import net.corda.crypto.impl.closeGracefully
import net.corda.crypto.impl.persistence.KeyValuePersistence
import net.corda.v5.base.types.toHexString
import net.corda.v5.cipher.suite.KeyEncodingService
import net.corda.v5.cipher.suite.WrappedKeyPair
import net.corda.v5.cipher.suite.schemes.SignatureScheme
import net.corda.v5.crypto.sha256Bytes
import java.security.PublicKey
import java.util.UUID

class SigningKeyCacheImpl(
    private val tenantId: String,
    private val keyEncoder: KeyEncodingService,
    persistenceFactory: KeyValuePersistenceFactory
) : SigningKeyCache, AutoCloseable {

    init {
        require(tenantId.isNotBlank()) { "The member id must not be blank." }
    }

    val persistence: KeyValuePersistence<SigningKeyRecord, SigningKeyRecord> =
        persistenceFactory.createSigningPersistence(tenantId, ::mutate)

    override fun find(publicKey: PublicKey): SigningKeyRecord? {
        val entity = persistence.get(toKeyDerivedId(publicKey))
        return if(entity != null && entity.tenantId != tenantId) {
            null
        } else {
            entity
        }
    }

    override fun find(alias: String): SigningKeyRecord? {
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
        val entity = SigningKeyRecord(
            tenantId = tenantId,
            category = category,
            alias = alias,
            hsmAlias = hsmAlias,
            publicKey = keyEncoder.encodeAsByteArray(publicKey),
            privateKeyMaterial = null,
            schemeCodeName = scheme.codeName,
            masterKeyAlias = null,
            externalId = null,
            version = 1
        )
        persistence.put(toKeyDerivedId(publicKey), entity)
        persistence.put(toAliasDerivedId(alias), entity)
    }

    override fun save(
        wrappedKeyPair: WrappedKeyPair,
        masterKeyAlias: String,
        scheme: SignatureScheme,
        externalId: UUID?
    ) {
        val entity = SigningKeyRecord(
            tenantId = tenantId,
            category = CryptoConsts.CryptoCategories.FRESH_KEYS,
            alias = null,
            hsmAlias = null,
            publicKey = keyEncoder.encodeAsByteArray(wrappedKeyPair.publicKey),
            privateKeyMaterial = wrappedKeyPair.keyMaterial,
            schemeCodeName = scheme.codeName,
            masterKeyAlias = masterKeyAlias,
            externalId = externalId,
            version = 1
        )
        persistence.put(toKeyDerivedId(wrappedKeyPair.publicKey), entity)
    }

    private fun mutate(entity: SigningKeyRecord): SigningKeyRecord = entity

    private fun toKeyDerivedId(publicKey: PublicKey): String =
        "$tenantId:${publicKey.sha256Bytes().toHexString()}"

    private fun toAliasDerivedId(alias: String) =
        "$tenantId:$alias"

    override fun close() {
        (persistence as? AutoCloseable)?.closeGracefully()
    }
}