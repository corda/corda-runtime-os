package net.corda.ledger.lib.dependencies.signing

import com.github.benmanes.caffeine.cache.Cache
import com.github.benmanes.caffeine.cache.Caffeine
import net.corda.cache.caffeine.CacheFactoryImpl
import net.corda.crypto.core.SigningKeyInfo
import net.corda.crypto.core.aes.WrappingKey
import net.corda.crypto.softhsm.impl.ShortHashCacheKey
import net.corda.crypto.softhsm.impl.WrappingRepositoryImpl
import net.corda.flow.application.crypto.SigningServiceImpl
import net.corda.ledger.lib.common.Constants.CACHE_EXPIRE_AFTER_MINS
import net.corda.ledger.lib.common.Constants.CACHE_MAX_SIZE
import net.corda.ledger.lib.dependencies.crypto.CryptoDependencies.cipherSchemeMetadata
import net.corda.ledger.lib.dependencies.crypto.CryptoDependencies.platformDigestService
import net.corda.ledger.lib.dependencies.db.DbDependencies.entityManagerFactory
import net.corda.ledger.lib.dependencies.sandbox.SandboxDependencies.currentSandboxGroupContext
import net.corda.ledger.lib.impl.stub.external.event.SigningServiceExternalEventExecutor
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
            entityManagerFactory = entityManagerFactory,
            tenantId = tenantId
        )
    }

    val layeredPropertyMapFactory = StubLayeredPropertyMapFactory()

    val signingRepoFactory = StubSigningRepositoryFactoryImpl(
        entityManagerFactory,
        cipherSchemeMetadata,
        platformDigestService,
        layeredPropertyMapFactory
    )

    val cryptoService = StubCryptoService(
        platformDigestService,
        privateKeyCache,
        wrappingKeyCache,
        shortHashCache,
        wrappingRepositoryFactory,
        emptyMap(),
        signingRepoFactory,
        cipherSchemeMetadata
    )

    val mySigningCache = StubSigningKeyCache()

    val signingServiceExecutor = SigningServiceExternalEventExecutor(cryptoService)

    val signingService = SigningServiceImpl(
        currentSandboxGroupContext,
        signingServiceExecutor,
        cipherSchemeMetadata,
        mySigningCache
    )
}