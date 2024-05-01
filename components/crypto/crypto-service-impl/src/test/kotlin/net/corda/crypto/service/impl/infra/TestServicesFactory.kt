package net.corda.crypto.service.impl.infra

import com.github.benmanes.caffeine.cache.Cache
import com.github.benmanes.caffeine.cache.Caffeine
import com.typesafe.config.ConfigFactory
import net.corda.cache.caffeine.CacheFactoryImpl
import net.corda.cipher.suite.impl.CipherSchemeMetadataImpl
import net.corda.cipher.suite.impl.DigestServiceImpl
import net.corda.cipher.suite.impl.PlatformDigestServiceImpl
import net.corda.cipher.suite.impl.SignatureVerificationServiceImpl
import net.corda.crypto.cipher.suite.CipherSchemeMetadata
import net.corda.crypto.cipher.suite.KeyEncodingService
import net.corda.crypto.cipher.suite.SignatureVerificationService
import net.corda.crypto.component.test.utils.TestConfigurationReadService
import net.corda.crypto.config.impl.KeyDerivationParameters
import net.corda.crypto.config.impl.createCryptoBootstrapParamsMap
import net.corda.crypto.config.impl.createDefaultCryptoConfig
import net.corda.crypto.core.CryptoConsts.SOFT_HSM_ID
import net.corda.crypto.core.CryptoService
import net.corda.crypto.core.SigningKeyInfo
import net.corda.crypto.core.aes.WrappingKeyImpl
import net.corda.crypto.persistence.WrappingKeyInfo
import net.corda.crypto.softhsm.impl.ShortHashCacheKey
import net.corda.crypto.softhsm.impl.SoftCryptoService
import net.corda.libs.configuration.SmartConfig
import net.corda.libs.configuration.SmartConfigFactory
import net.corda.lifecycle.LifecycleStatus
import net.corda.lifecycle.test.impl.TestLifecycleCoordinatorFactoryImpl
import net.corda.schema.configuration.ConfigKeys
import net.corda.test.util.eventually
import org.mockito.kotlin.mock
import java.security.KeyPairGenerator
import java.security.Provider
import java.util.concurrent.TimeUnit
import kotlin.test.assertEquals


/**
 * Provide instances of high level crypto services, with no database underneath, for
 * use for integration test cases that don't involve in-memory databases.
 */
class TestServicesFactory {
    companion object {
        const val CTX_TRACKING = "ctxTrackingId"
        const val CUSTOM1_HSM_ID = "CUSTOM1"
        const val CUSTOM2_HSM_ID = "CUSTOM2"
    }

    val configFactory = SmartConfigFactory.createWithoutSecurityServices()
    val emptyConfig: SmartConfig = configFactory.create(ConfigFactory.empty())

    val cryptoConfig: SmartConfig = configFactory.create(
        createDefaultCryptoConfig(listOf(KeyDerivationParameters( "passphrase", "salt")))
    ).withFallback(
        ConfigFactory.parseString(
            """
{
    "hsmMap": {
        "CUSTOM1": {
            "retry": {
                "maxAttempts": 3,
                "attemptTimeoutMills": 20000
            },
            "hsm": {
                "name": "CUSTOM",
                "categories": [
                    {
                        "category": "*",
                        "policy": "ALIASED"
                    }
                ],
                "masterKeyPolicy": "SHARED",
                "masterKeyAlias": "cordawrappingkey",
                "capacity": "2",
                "supportedSchemes": [
                    "CORDA.ECDSA.SECP256R1"
                ],
                "config": {
                    "username": "user1"
                }
            },
        },
        "CUSTOM2": {
            "retry": {
                "maxAttempts": 3,
                "attemptTimeoutMills": 20000
            },
            "hsm": {
                "name": "CUSTOM",
                "categories": [
                    {
                        "category": "*",
                        "policy": "WRAPPED"
                    }
                ],
                "masterKeyPolicy": "SHARED",
                "masterKeyAlias": "cordawrappingkey",
                "capacity": "2",
                "supportedSchemes": [
                    "CORDA.ECDSA.SECP256R1"
                ],
                "config": {
                    "username": "user2"
                }
            }
        }
    }
}                
""".trimIndent()
        )
    )

    val bootstrapConfig = cryptoConfig.factory.create(
        ConfigFactory.parseMap(createCryptoBootstrapParamsMap(SOFT_HSM_ID))
    )

    val schemeMetadata: CipherSchemeMetadata = CipherSchemeMetadataImpl()

    val coordinatorFactory: TestLifecycleCoordinatorFactoryImpl = TestLifecycleCoordinatorFactoryImpl()

    val platformDigest = PlatformDigestServiceImpl(schemeMetadata)
    val digest = DigestServiceImpl(platformDigest, null)

    val verifier: SignatureVerificationService =
        SignatureVerificationServiceImpl(schemeMetadata, digest)

    val configurationReadService: TestConfigurationReadService by lazy {
        TestConfigurationReadService(
            coordinatorFactory,
            listOf(
                ConfigKeys.MESSAGING_CONFIG to emptyConfig,
                ConfigKeys.CRYPTO_CONFIG to cryptoConfig
            )
        ).also {
            it.start()
            eventually {
                assertEquals(LifecycleStatus.UP, it.lifecycleCoordinator.status)
            }
        }
    }

    val signingRepository: TestSigningRepository by lazy { TestSigningRepository() }

    val tenantInfoService: TestTenantInfoService by lazy { TestTenantInfoService() }


    val rootWrappingKey = WrappingKeyImpl.generateWrappingKey(schemeMetadata)
    val secondLevelWrappingKey = WrappingKeyImpl.generateWrappingKey(schemeMetadata)
    val secondLevelWrappingKeyWrapped = rootWrappingKey.wrap(secondLevelWrappingKey)
    val secondLevelWrappingKeyInfo = WrappingKeyInfo(
        1, "AES", secondLevelWrappingKeyWrapped, 1, "root", "key1")
    val wrappingRepository = TestWrappingRepository(secondLevelWrappingKeyInfo)
    val shortHashCache: Cache<ShortHashCacheKey, SigningKeyInfo> = CacheFactoryImpl().build(
        "test short hash cache", Caffeine.newBuilder()
            .expireAfterAccess(3600, TimeUnit.MINUTES)
            .maximumSize(20)
    )
    val cryptoService: CryptoService by lazy {
        SoftCryptoService(
            wrappingRepositoryFactory = { wrappingRepository },
            schemeMetadata = schemeMetadata,
            defaultUnmanagedWrappingKeyName = "root",
            unmanagedWrappingKeys = mapOf("root" to rootWrappingKey),
            digestService = PlatformDigestServiceImpl(schemeMetadata),
            wrappingKeyCache = null,
            privateKeyCache = null,
            shortHashCache = shortHashCache,
            keyPairGeneratorFactory = { algorithm: String, provider: Provider ->
                KeyPairGenerator.getInstance(algorithm, provider)
            },
            wrappingKeyFactory = {
                WrappingKeyImpl.generateWrappingKey(it)
            },
            signingRepositoryFactory = {
                signingRepository
            },
            clusterDbInfoService = tenantInfoService
        )
    }

    val keyEncodingService = mock<KeyEncodingService> {
    }
}


