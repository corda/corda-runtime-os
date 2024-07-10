package net.corda.ledger.lib.dependencies.signing

import com.github.benmanes.caffeine.cache.Cache
import com.github.benmanes.caffeine.cache.Caffeine
import net.corda.cache.caffeine.CacheFactoryImpl
import net.corda.cipher.suite.impl.CipherSchemeMetadataImpl
import net.corda.crypto.cipher.suite.GeneratedWrappedKey
import net.corda.crypto.core.SigningKeyInfo
import net.corda.crypto.core.aes.WrappingKey
import net.corda.crypto.core.aes.WrappingKeyImpl
import net.corda.crypto.persistence.SigningWrappedKeySaveContext
import net.corda.crypto.softhsm.impl.PRIVATE_KEY_ENCODING_VERSION
import net.corda.crypto.softhsm.impl.ShortHashCacheKey
import net.corda.crypto.softhsm.impl.WrappingRepositoryImpl
import net.corda.flow.application.crypto.SigningServiceImpl
import net.corda.ledger.lib.common.Constants.CACHE_EXPIRE_AFTER_MINS
import net.corda.ledger.lib.common.Constants.CACHE_MAX_SIZE
import net.corda.ledger.lib.common.Constants.TENANT_ID
import net.corda.ledger.lib.dependencies.crypto.CryptoDependencies.cipherSchemeMetadata
import net.corda.ledger.lib.dependencies.crypto.CryptoDependencies.platformDigestService
import net.corda.ledger.lib.dependencies.db.DbDependencies.cryptoEntityManagerFactory
import net.corda.ledger.lib.dependencies.db.DbDependencies.cryptoEntityManagerFactory2
import net.corda.ledger.lib.dependencies.sandbox.SandboxDependencies.currentSandboxGroupContext
import net.corda.ledger.lib.impl.stub.external.event.SigningServiceExternalEventExecutor
import net.corda.ledger.lib.impl.stub.signing.StubCryptoService
import net.corda.ledger.lib.impl.stub.signing.StubSigningKeyCache
import net.corda.ledger.lib.impl.stub.signing.StubSigningRepositoryFactoryImpl
import net.corda.ledger.lib.keyPairExample
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
            "ZkM+H9abV6UIZ4ClRxxOZuxKhkZPrAnjo5Os5N+CAro=",
            "ZKV/XqjxYZQPctFqLX3RcopZ3ckE8ofDhhkHo/VfuIM="
        )),
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