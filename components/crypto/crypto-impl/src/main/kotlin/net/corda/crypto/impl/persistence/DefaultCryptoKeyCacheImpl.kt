package net.corda.crypto.impl.persistence

import net.corda.v5.base.util.contextLogger
import net.corda.v5.cipher.suite.CipherSchemeMetadata
import net.corda.v5.cipher.suite.schemes.SignatureScheme
import java.security.KeyPair

@Suppress("LongParameterList")
open class DefaultCryptoKeyCacheImpl(
    private val memberId: String,
    passphrase: String?,
    salt: String?,
    private val schemeMetadata: CipherSchemeMetadata,
    private val persistence: PersistentCache<DefaultCryptoCachedKeyInfo, DefaultCryptoPersistentKeyInfo>
) : DefaultCryptoKeyCache, AutoCloseable {
    companion object {
        private val logger = contextLogger()
    }

    init {
        require(memberId.isNotBlank()) { "The member id must not be blank."}
    }

    private val masterKey: WrappingKey = WrappingKey.deriveMasterKey(schemeMetadata, passphrase, salt)

    override fun save(alias: String, keyPair: KeyPair, scheme: SignatureScheme) {
        logger.debug("Saving key with alias={} for member={}", alias, memberId)
        val partitionedAlias = effectiveAlias(alias)
        val entity = DefaultCryptoPersistentKeyInfo(
            memberId = memberId,
            alias = partitionedAlias,
            publicKey = schemeMetadata.encodeAsByteArray(keyPair.public),
            privateKey = masterKey.wrap(keyPair.private),
            algorithmName = scheme.algorithmName,
            version = 1
        )
        persistence.put(partitionedAlias, entity) {
            DefaultCryptoCachedKeyInfo(
                memberId = memberId,
                publicKey = keyPair.public,
                privateKey = keyPair.private
            )
        }
    }

    override fun save(alias: String, key: WrappingKey) {
        logger.debug("Saving wrapping key with alias={} for member={}", alias, memberId)
        val partitionedAlias = effectiveAlias(alias)
        val entity = DefaultCryptoPersistentKeyInfo(
            memberId = memberId,
            alias = partitionedAlias,
            publicKey = null,
            privateKey = masterKey.wrap(key),
            algorithmName = WrappingKey.WRAPPING_KEY_ALGORITHM,
            version = 1
        )
        persistence.put(partitionedAlias, entity) {
            DefaultCryptoCachedKeyInfo(
                memberId = memberId,
                wrappingKey = key
            )
        }
    }

    @Suppress("TooGenericExceptionCaught")
    override fun find(alias: String): DefaultCryptoCachedKeyInfo? {
        logger.debug("Looking for public key with alias={} for member={}", alias, memberId)
        val partitionedAlias = effectiveAlias(alias)
        return persistence.get(partitionedAlias) { entity ->
            if (entity.publicKey != null) {
                DefaultCryptoCachedKeyInfo(
                    memberId = entity.memberId,
                    publicKey = schemeMetadata.decodePublicKey(entity.publicKey!!),
                    privateKey = masterKey.unwrap(entity.privateKey)
                )
            } else {
                DefaultCryptoCachedKeyInfo(
                    memberId = entity.memberId,
                    wrappingKey = masterKey.unwrapWrappingKey(entity.privateKey)
                )
            }
        }
    }

    private fun effectiveAlias(alias: String) =
        "$memberId:$alias"

    override fun close() {
        (persistence as? AutoCloseable)?.close()
    }
}

