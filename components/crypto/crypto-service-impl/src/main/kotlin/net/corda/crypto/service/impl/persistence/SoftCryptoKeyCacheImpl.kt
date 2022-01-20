package net.corda.crypto.service.impl.persistence

import net.corda.crypto.component.persistence.EntityKeyInfo
import net.corda.crypto.component.persistence.KeyValuePersistence
import net.corda.crypto.component.persistence.CachedSoftKeysRecord
import net.corda.crypto.component.persistence.SoftKeysPersistenceProvider
import net.corda.crypto.component.persistence.WrappingKey
import net.corda.data.crypto.persistence.SoftKeysRecord
import net.corda.v5.base.util.contextLogger
import net.corda.v5.cipher.suite.CipherSchemeMetadata
import net.corda.v5.cipher.suite.schemes.SignatureScheme
import java.nio.ByteBuffer
import java.security.KeyPair
import java.time.Instant

@Suppress("LongParameterList")
class SoftCryptoKeyCacheImpl(
    private val tenantId: String,
    passphrase: String?,
    salt: String?,
    private val schemeMetadata: CipherSchemeMetadata,
    persistenceFactory: SoftKeysPersistenceProvider
) : SoftCryptoKeyCache, AutoCloseable {
    companion object {
        private val logger = contextLogger()
    }

    init {
        require(tenantId.isNotBlank()) { "The member id must not be blank."}
    }

    val persistence: KeyValuePersistence<CachedSoftKeysRecord, SoftKeysRecord> =
        persistenceFactory.getInstance(tenantId, ::mutate)

    private val masterKey: WrappingKey = WrappingKey.deriveMasterKey(schemeMetadata, passphrase, salt)

    override fun save(alias: String, keyPair: KeyPair, scheme: SignatureScheme) {
        logger.debug("Saving key with alias={} for tenant={}", alias, tenantId)
        val entity = SoftKeysRecord(
            tenantId,
            alias,
            ByteBuffer.wrap(schemeMetadata.encodeAsByteArray(keyPair.public)),
            ByteBuffer.wrap(masterKey.wrap(keyPair.private)),
            scheme.algorithmName,
            1,
            Instant.now()
        )
        persistence.put(
            entity,
            EntityKeyInfo(EntityKeyInfo.ALIAS, toAliasDerivedId(alias))
        )
    }

    override fun save(alias: String, key: WrappingKey) {
        logger.debug("Saving wrapping key with alias={} for tenant={}", alias, tenantId)
        val entity = SoftKeysRecord(
            tenantId,
            alias,
            null,
            ByteBuffer.wrap(masterKey.wrap(key)),
            WrappingKey.WRAPPING_KEY_ALGORITHM,
            1,
            Instant.now()
        )
        persistence.put(
            entity,
            EntityKeyInfo(EntityKeyInfo.ALIAS, toAliasDerivedId(alias))
        )
    }

    override fun find(alias: String): CachedSoftKeysRecord? {
        logger.debug("Looking for public key with alias={} for tenant={}", alias, tenantId)
        val entity = persistence.get(toAliasDerivedId(alias))
        return if(entity != null && entity.tenantId != tenantId) {
            null
        } else {
            entity
        }
    }

    private fun mutate(entity: SoftKeysRecord): CachedSoftKeysRecord =
        if (entity.publicKey != null) {
            CachedSoftKeysRecord(
                tenantId = entity.tenantId,
                publicKey = schemeMetadata.decodePublicKey(entity.publicKey!!.array()),
                privateKey = masterKey.unwrap(entity.privateKey.array())
            )
        } else {
            CachedSoftKeysRecord(
                tenantId = entity.tenantId,
                wrappingKey = masterKey.unwrapWrappingKey(entity.privateKey.array())
            )
        }

    private fun toAliasDerivedId(alias: String) =
        "$tenantId:$alias"

    override fun close() {
        (persistence as? AutoCloseable)?.close()
    }
}

