package net.corda.crypto.impl.persistence

import net.corda.v5.base.util.contextLogger
import net.corda.v5.cipher.suite.CipherSchemeMetadata
import net.corda.v5.cipher.suite.schemes.SignatureScheme
import java.security.KeyPair

@Suppress("LongParameterList")
class DefaultCryptoKeyCacheImpl(
    private val memberId: String,
    passphrase: String?,
    salt: String?,
    private val schemeMetadata: CipherSchemeMetadata,
    persistenceFactory: KeyValuePersistenceFactory
) : DefaultCryptoKeyCache, AutoCloseable {
    companion object {
        private val logger = contextLogger()
    }

    init {
        require(memberId.isNotBlank()) { "The member id must not be blank."}
    }

    val persistence: KeyValuePersistence<DefaultCryptoCachedKeyInfo, DefaultCryptoPersistentKeyInfo> =
        persistenceFactory.createDefaultCryptoPersistence(memberId, ::mutate)

    private val masterKey: WrappingKey = WrappingKey.deriveMasterKey(schemeMetadata, passphrase, salt)

    override fun save(alias: String, keyPair: KeyPair, scheme: SignatureScheme) {
        logger.debug("Saving key with alias={} for member={}", alias, memberId)
        val partitionedAlias = effectiveAlias(alias)
        val entity = DefaultCryptoPersistentKeyInfo(
            tenantId = memberId,
            alias = partitionedAlias,
            publicKey = schemeMetadata.encodeAsByteArray(keyPair.public),
            privateKey = masterKey.wrap(keyPair.private),
            algorithmName = scheme.algorithmName,
            version = 1
        )
        persistence.put(partitionedAlias, entity)
    }

    override fun save(alias: String, key: WrappingKey) {
        logger.debug("Saving wrapping key with alias={} for member={}", alias, memberId)
        val partitionedAlias = effectiveAlias(alias)
        val entity = DefaultCryptoPersistentKeyInfo(
            tenantId = memberId,
            alias = partitionedAlias,
            publicKey = null,
            privateKey = masterKey.wrap(key),
            algorithmName = WrappingKey.WRAPPING_KEY_ALGORITHM,
            version = 1
        )
        persistence.put(partitionedAlias, entity)
    }

    override fun find(alias: String): DefaultCryptoCachedKeyInfo? {
        logger.debug("Looking for public key with alias={} for member={}", alias, memberId)
        val partitionedAlias = effectiveAlias(alias)
        return persistence.get(partitionedAlias)
    }

    private fun mutate(entity: DefaultCryptoPersistentKeyInfo): DefaultCryptoCachedKeyInfo =
        if (entity.publicKey != null) {
            DefaultCryptoCachedKeyInfo(
                tenantId = entity.tenantId,
                publicKey = schemeMetadata.decodePublicKey(entity.publicKey!!),
                privateKey = masterKey.unwrap(entity.privateKey)
            )
        } else {
            DefaultCryptoCachedKeyInfo(
                tenantId = entity.tenantId,
                wrappingKey = masterKey.unwrapWrappingKey(entity.privateKey)
            )
        }

    private fun effectiveAlias(alias: String) =
        "$memberId:$alias"

    override fun close() {
        (persistence as? AutoCloseable)?.close()
    }
}

