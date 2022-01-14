package net.corda.crypto.service.persistence

import net.corda.crypto.component.persistence.KeyValuePersistenceFactory
import net.corda.crypto.component.persistence.SoftCryptoKeyRecord
import net.corda.crypto.component.persistence.SoftCryptoKeyRecordInfo
import net.corda.crypto.component.persistence.WrappingKey
import net.corda.crypto.impl.persistence.KeyValuePersistence
import net.corda.v5.base.util.contextLogger
import net.corda.v5.cipher.suite.CipherSchemeMetadata
import net.corda.v5.cipher.suite.schemes.SignatureScheme
import java.security.KeyPair

@Suppress("LongParameterList")
class SoftCryptoKeyCacheImpl(
    private val tenantId: String,
    passphrase: String?,
    salt: String?,
    private val schemeMetadata: CipherSchemeMetadata,
    persistenceFactory: KeyValuePersistenceFactory
) : SoftCryptoKeyCache, AutoCloseable {
    companion object {
        private val logger = contextLogger()
    }

    init {
        require(tenantId.isNotBlank()) { "The member id must not be blank."}
    }

    val persistence: KeyValuePersistence<SoftCryptoKeyRecordInfo, SoftCryptoKeyRecord> =
        persistenceFactory.createDefaultCryptoPersistence(tenantId, ::mutate)

    private val masterKey: WrappingKey = WrappingKey.deriveMasterKey(schemeMetadata, passphrase, salt)

    override fun save(alias: String, keyPair: KeyPair, scheme: SignatureScheme) {
        logger.debug("Saving key with alias={} for tenant={}", alias, tenantId)
        val entity = SoftCryptoKeyRecord(
            tenantId = tenantId,
            alias = alias,
            publicKey = schemeMetadata.encodeAsByteArray(keyPair.public),
            privateKey = masterKey.wrap(keyPair.private),
            algorithmName = scheme.algorithmName,
            version = 1
        )
        persistence.put(aliasDerivedId(alias), entity)
    }

    override fun save(alias: String, key: WrappingKey) {
        logger.debug("Saving wrapping key with alias={} for tenant={}", alias, tenantId)
        val entity = SoftCryptoKeyRecord(
            tenantId = tenantId,
            alias = alias,
            publicKey = null,
            privateKey = masterKey.wrap(key),
            algorithmName = WrappingKey.WRAPPING_KEY_ALGORITHM,
            version = 1
        )
        persistence.put(aliasDerivedId(alias), entity)
    }

    override fun find(alias: String): SoftCryptoKeyRecordInfo? {
        logger.debug("Looking for public key with alias={} for tenant={}", alias, tenantId)
        val entity = persistence.get(aliasDerivedId(alias))
        return if(entity != null && entity.tenantId != tenantId) {
            null
        } else {
            entity
        }
    }

    private fun mutate(entity: SoftCryptoKeyRecord): SoftCryptoKeyRecordInfo =
        if (entity.publicKey != null) {
            SoftCryptoKeyRecordInfo(
                tenantId = entity.tenantId,
                publicKey = schemeMetadata.decodePublicKey(entity.publicKey!!),
                privateKey = masterKey.unwrap(entity.privateKey)
            )
        } else {
            SoftCryptoKeyRecordInfo(
                tenantId = entity.tenantId,
                wrappingKey = masterKey.unwrapWrappingKey(entity.privateKey)
            )
        }

    private fun aliasDerivedId(alias: String) =
        "$tenantId:$alias"

    override fun close() {
        (persistence as? AutoCloseable)?.close()
    }
}

