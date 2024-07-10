package net.corda.ledger.lib.dependencies.signing

import com.github.benmanes.caffeine.cache.Cache
import com.github.benmanes.caffeine.cache.Caffeine
import net.corda.cache.caffeine.CacheFactoryImpl
import net.corda.cipher.suite.impl.CipherSchemeMetadataImpl
import net.corda.crypto.core.ShortHash
import net.corda.crypto.core.SigningKeyInfo
import net.corda.crypto.core.aes.WrappingKey
import net.corda.crypto.core.aes.WrappingKeyImpl
import net.corda.crypto.core.fullIdHash
import net.corda.crypto.softhsm.impl.ShortHashCacheKey
import net.corda.crypto.softhsm.impl.WrappingRepositoryImpl
import net.corda.flow.application.crypto.SigningServiceImpl
import net.corda.flow.application.crypto.external.events.SignParameters
import net.corda.ledger.lib.common.Constants.CACHE_EXPIRE_AFTER_MINS
import net.corda.ledger.lib.common.Constants.CACHE_MAX_SIZE
import net.corda.ledger.lib.common.Constants.TENANT_ID
import net.corda.ledger.lib.dependencies.crypto.CryptoDependencies.cipherSchemeMetadata
import net.corda.ledger.lib.dependencies.crypto.CryptoDependencies.platformDigestService
import net.corda.ledger.lib.dependencies.db.DbDependencies.cryptoEntityManagerFactory
import net.corda.ledger.lib.dependencies.db.DbDependencies.cryptoEntityManagerFactory2
import net.corda.ledger.lib.dependencies.sandbox.SandboxDependencies.currentSandboxGroupContext
import net.corda.ledger.lib.impl.stub.external.event.ExternalEventCallback
import net.corda.ledger.lib.impl.stub.signing.StubCryptoService
import net.corda.ledger.lib.impl.stub.signing.StubSigningKeyCache
import net.corda.ledger.lib.impl.stub.signing.StubSigningRepositoryFactoryImpl
import java.security.PrivateKey
import java.security.PublicKey
import java.util.concurrent.TimeUnit

object SigningDependencies {

    val wrappingKeyCache: Cache<String, WrappingKey> = CacheFactoryImpl().build(
        "HSM-Wrapping-Keys-Map",
        Caffeine.newBuilder()
            .expireAfterAccess(CACHE_EXPIRE_AFTER_MINS, TimeUnit.MINUTES)
            .maximumSize(CACHE_MAX_SIZE)
    )

    val privateKeyCache: Cache<PublicKey, PrivateKey> = CacheFactoryImpl().build(
        "HSM-Soft-Keys-Map",
        Caffeine.newBuilder()
            .expireAfterAccess(CACHE_EXPIRE_AFTER_MINS, TimeUnit.MINUTES)
            .maximumSize(CACHE_MAX_SIZE)
    )
    val shortHashCache: Cache<ShortHashCacheKey, SigningKeyInfo> = CacheFactoryImpl().build(
        "Signing-Key-Cache",
        Caffeine.newBuilder()
            .expireAfterAccess(CACHE_EXPIRE_AFTER_MINS, TimeUnit.MINUTES)
            .maximumSize(CACHE_MAX_SIZE)
    )

    val wrappingRepositoryFactory = { tenantId: String ->
        WrappingRepositoryImpl(
            entityManagerFactory = cryptoEntityManagerFactory2,
            tenantId = tenantId
        )
    }

    val layeredPropertyMapFactory = StubLayeredPropertyMapFactory()

    val signingRepoFactory = StubSigningRepositoryFactoryImpl(
        cryptoEntityManagerFactory,
        cipherSchemeMetadata,
        platformDigestService,
        layeredPropertyMapFactory
    )

    /**
     * "defaultWrappingKey" : "root1",
     * "wrappingKeys" : [
     *      {"alias": "root1", "passphrase": "B"}
     * ]
     */
    val cryptoService = StubCryptoService(
        platformDigestService,
        privateKeyCache,
        wrappingKeyCache,
        shortHashCache,
        wrappingRepositoryFactory,
        mapOf("root1" to WrappingKeyImpl.derive(
            CipherSchemeMetadataImpl(),
            // TODO Unfortunately these are hardcoded for now since we use an existing DB/key
            "ZkM+H9abV6UIZ4ClRxxOZuxKhkZPrAnjo5Os5N+CAro=",
            "ZKV/XqjxYZQPctFqLX3RcopZ3ckE8ofDhhkHo/VfuIM="
        )),
        signingRepoFactory,
        cipherSchemeMetadata
    )

    val mySigningCache = StubSigningKeyCache()

    val signingService = SigningServiceImpl(
        currentSandboxGroupContext,
        @Suppress("UNCHECKED_CAST")
        ExternalEventCallback { factoryClass, parameters ->
            when (parameters) {
                // If we get a Set as parameters it means we need to do signing key lookup
                is Set<*> -> {
                    val keySet = parameters as Set<PublicKey>
                    cryptoService.lookupSigningKeysByPublicKeyShortHash(
                        TENANT_ID,
                        keySet.map { ShortHash.Companion.of(it.fullIdHash()) }
                    ).map { it.publicKey }
                }
                // If we get a SignParameters it means we need to do signing
                is SignParameters -> {
                    val decodedPublicKey = cipherSchemeMetadata.decodePublicKey(parameters.encodedPublicKeyBytes)
                    cryptoService.sign(
                        TENANT_ID,
                        decodedPublicKey,
                        parameters.signatureSpec,
                        parameters.bytes,
                        emptyMap()
                    )
                }
                // Otherwise we don't support it for now
                else -> throw IllegalArgumentException("currently not supported")
            }
        },
        cipherSchemeMetadata,
        mySigningCache
    )
}