package net.corda.impl.cipher.suite

import net.corda.impl.caching.crypto.SimplePersistentCache
import net.corda.impl.caching.crypto.SimplePersistentCacheFactory
import net.corda.v5.base.types.toHexString
import net.corda.v5.base.util.contextLogger
import net.corda.v5.cipher.suite.CipherSchemeMetadata
import net.corda.v5.cipher.suite.schemes.SignatureScheme
import net.corda.v5.crypto.sha256Bytes
import java.security.KeyPair
import java.security.PrivateKey
import java.security.PublicKey

@Suppress("LongParameterList")
open class DefaultKeyCacheImpl(
    sandboxId: String,
    partition: String?,
    passphrase: String?,
    salt: String?,
    private val schemeMetadata: CipherSchemeMetadata,
    private val cacheFactory: SimplePersistentCacheFactory<DefaultCachedKey, DefaultCryptoPersistentKey>
) : DefaultKeyCache, AutoCloseable {
    companion object {
        private val logger = contextLogger()
    }

    // to avoid creating cache when closing if it still wasn't created
    private var cacheCreated = false

    private val cache: SimplePersistentCache<DefaultCachedKey, DefaultCryptoPersistentKey> by lazy {
        cacheCreated = true
        cacheFactory.create()
    }

    private val masterKey: WrappingKey = WrappingKey.deriveMasterKey(schemeMetadata, passphrase, salt)

    private val sandboxId: String = if (sandboxId.isBlank()) {
        ""
    } else {
        if (sandboxId.length > 64) {
            sandboxId.toByteArray().sha256Bytes().toHexString().toLowerCase()
        } else {
            sandboxId.toLowerCase()
        }
    }

    private val partition: String = if (partition.isNullOrBlank()) {
        ""
    } else {
        if (partition.length > 64) {
            partition.toByteArray().sha256Bytes().toHexString().toLowerCase()
        } else {
            partition.toLowerCase()
        }
    }

    override fun save(alias: String, keyPair: KeyPair, scheme: SignatureScheme) {
        logger.debug("Saving key with alias={} in partition={}", alias, partition)
        val partitionedAlias = effectiveAlias(alias)
        val entity = DefaultCryptoPersistentKey(
            sandboxId = sandboxId,
            partition = partition,
            alias = partitionedAlias,
            publicKey = schemeMetadata.encodeAsByteArray(keyPair.public),
            privateKey = masterKey.wrap(keyPair.private),
            algorithmName = scheme.algorithmName,
            version = 1
        )
        cache.put(partitionedAlias, entity) {
            DefaultCachedKey(
                publicKey = keyPair.public,
                privateKey = keyPair.private
            )
        }
    }

    override fun save(alias: String, key: WrappingKey) {
        logger.debug("Saving wrapping key with alias={} in partition={}", alias, partition)
        val partitionedAlias = effectiveAlias(alias)
        val entity = DefaultCryptoPersistentKey(
            sandboxId = sandboxId,
            partition = partition,
            alias = partitionedAlias,
            publicKey = null,
            privateKey = masterKey.wrap(key),
            algorithmName = WrappingKey.WRAPPING_KEY_ALGORITHM,
            version = 1
        )
        cache.put(partitionedAlias, entity) {
            DefaultCachedKey(
                wrappingKey = key
            )
        }
    }

    @Suppress("TooGenericExceptionCaught")
    override fun find(alias: String): DefaultCachedKey? {
        logger.debug("Looking for public key with alias={} in partition={}", alias, partition)
        val partitionedAlias = effectiveAlias(alias)
        return cache.get(partitionedAlias, ::mutator)
    }

    private fun effectiveAlias(alias: String) = "$sandboxId:$partition:$alias"

    private fun mutator(entity: DefaultCryptoPersistentKey): DefaultCachedKey = if (entity.publicKey != null) {
        DefaultCachedKey(
            publicKey = schemeMetadata.decodePublicKey(entity.publicKey!!),
            privateKey = masterKey.unwrap(entity.privateKey)
        )
    } else {
        DefaultCachedKey(
            wrappingKey = masterKey.unwrapWrappingKey(entity.privateKey)
        )
    }

    @Suppress("TooGenericExceptionCaught")
    override fun close() {
        try {
            if (cacheCreated) {
                (cache as? AutoCloseable)?.close()
            }
        } catch (e: Exception) {
            // intentional
        }
    }
}

class DefaultCachedKey(
    val publicKey: PublicKey? = null,
    var privateKey: PrivateKey? = null,
    var wrappingKey: WrappingKey? = null
)
