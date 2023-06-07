package net.corda.crypto.service.impl.infra

import com.github.benmanes.caffeine.cache.Caffeine
import com.typesafe.config.ConfigFactory
import net.corda.cache.caffeine.CacheFactoryImpl
import java.security.KeyPairGenerator
import java.security.Provider
import java.util.concurrent.ConcurrentHashMap
import net.corda.cipher.suite.impl.CipherSchemeMetadataImpl
import net.corda.cipher.suite.impl.DigestServiceImpl
import net.corda.cipher.suite.impl.PlatformDigestServiceImpl
import net.corda.cipher.suite.impl.SignatureVerificationServiceImpl
import net.corda.crypto.cipher.suite.CipherSchemeMetadata
import net.corda.crypto.cipher.suite.CryptoService
import net.corda.crypto.cipher.suite.CryptoServiceExtensions
import net.corda.crypto.cipher.suite.GeneratedWrappedKey
import net.corda.crypto.cipher.suite.KeyGenerationSpec
import net.corda.crypto.cipher.suite.SharedSecretSpec
import net.corda.crypto.cipher.suite.SignatureVerificationService
import net.corda.crypto.cipher.suite.SigningWrappedSpec
import net.corda.crypto.cipher.suite.schemes.KeyScheme
import net.corda.crypto.component.test.utils.TestConfigurationReadService
import net.corda.crypto.config.impl.createCryptoBootstrapParamsMap
import net.corda.crypto.config.impl.createDefaultCryptoConfig
import net.corda.crypto.config.impl.signingService
import net.corda.crypto.core.CryptoConsts.SOFT_HSM_ID
import net.corda.crypto.core.aes.WrappingKeyImpl
import net.corda.crypto.service.SigningService
import net.corda.crypto.service.impl.HSMServiceImpl
import net.corda.crypto.service.impl.SigningServiceImpl
import net.corda.crypto.softhsm.impl.SoftCryptoService
import net.corda.libs.configuration.SmartConfig
import net.corda.libs.configuration.SmartConfigFactory
import net.corda.lifecycle.LifecycleStatus
import net.corda.lifecycle.test.impl.TestLifecycleCoordinatorFactoryImpl
import net.corda.schema.configuration.ConfigKeys
import net.corda.test.util.eventually
import net.corda.v5.crypto.SignatureSpec
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

    val recordedCryptoContexts = ConcurrentHashMap<String, Map<String, String>>()

    val configFactory = SmartConfigFactory.createWithoutSecurityServices()
    val emptyConfig: SmartConfig = configFactory.create(ConfigFactory.empty())

    val cryptoConfig: SmartConfig = configFactory.create(
        createDefaultCryptoConfig("salt", "passphrase")
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

    val signingRepository: TestSigningRepository by lazy {
        TestSigningRepository()
    }

    val hsmStore: TestHSMStore by lazy {
        TestHSMStore(coordinatorFactory).also {
            it.start()
            eventually {
                assertEquals(LifecycleStatus.UP, it.lifecycleCoordinator.status)
            }
        }
    }

    val cryptoWrappingRepository = TestWrappingRepository()


    val rootWrappingKey = WrappingKeyImpl.generateWrappingKey(schemeMetadata)

    val cryptoService: CryptoService by lazy {
        CryptoServiceWrapper(
            SoftCryptoService(
                wrappingRepositoryFactory = { cryptoWrappingRepository },
                schemeMetadata = schemeMetadata,
                defaultUnmanagedWrappingKeyName = "test",
                unmanagedWrappingKeys = mapOf( "test" to rootWrappingKey),
                digestService = PlatformDigestServiceImpl(schemeMetadata),
                wrappingKeyCache = null,
                privateKeyCache = null,
                keyPairGeneratorFactory = { algorithm: String, provider: Provider ->
                    KeyPairGenerator.getInstance(algorithm, provider)
                },
                wrappingKeyFactory = {
                    WrappingKeyImpl.generateWrappingKey(it)
                },
                signingRepositoryFactory =  {
                    signingRepository
                }
            ),
            recordedCryptoContexts
        )
    }

    val signingConfig = cryptoConfig.signingService()
    val signingService: SigningService by lazy {
        SigningServiceImpl(
            cryptoService = cryptoService,
            signingRepositoryFactory = { signingRepository },
            digestService = PlatformDigestServiceImpl(schemeMetadata),
            schemeMetadata = schemeMetadata,
            signingKeyInfoCache = CacheFactoryImpl().build(
                "Signing-Key-Cache",
                Caffeine.newBuilder()
                    .expireAfterAccess(signingConfig.cache.expireAfterAccessMins, TimeUnit.MINUTES)
                    .maximumSize(signingConfig.cache.maximumSize)
            ),
            hsmStore = hsmStore
        )
    }

    val hsmService: HSMServiceImpl by lazy {
        HSMServiceImpl(hsmStore, cryptoService)
    }

    private class CryptoServiceWrapper(
        private val impl: CryptoService,
        private val recordedCryptoContexts: ConcurrentHashMap<String, Map<String, String>>
    ) : CryptoService {
        override val extensions: List<CryptoServiceExtensions> get() = impl.extensions

        override val supportedSchemes: Map<KeyScheme, List<SignatureSpec>> get() = impl.supportedSchemes

        override fun createWrappingKey(wrappingKeyAlias: String, failIfExists: Boolean, context: Map<String, String>) {
            if (context.containsKey("ctxTrackingId")) {
                recordedCryptoContexts[context.getValue("ctxTrackingId")] = context
            }
            impl.createWrappingKey(wrappingKeyAlias, failIfExists, context)
        }

        override fun generateKeyPair(spec: KeyGenerationSpec, context: Map<String, String>): GeneratedWrappedKey {
            if (context.containsKey("ctxTrackingId")) {
                recordedCryptoContexts[context.getValue("ctxTrackingId")] = context
            }
            return impl.generateKeyPair(spec, context)
        }

        override fun sign(spec: SigningWrappedSpec, data: ByteArray, context: Map<String, String>): ByteArray {
            if (context.containsKey("ctxTrackingId")) {
                recordedCryptoContexts[context.getValue("ctxTrackingId")] = context
            }
            return impl.sign(spec, data, context)
        }

        override fun delete(alias: String, context: Map<String, String>): Boolean {
            if (context.containsKey("ctxTrackingId")) {
                recordedCryptoContexts[context.getValue("ctxTrackingId")] = context
            }
            return impl.delete(alias, context)
        }

        override fun deriveSharedSecret(spec: SharedSecretSpec, context: Map<String, String>): ByteArray {
            if (context.containsKey("ctxTrackingId")) {
                recordedCryptoContexts[context.getValue("ctxTrackingId")] = context
            }
            return impl.deriveSharedSecret(spec, context)
        }
    }
}


