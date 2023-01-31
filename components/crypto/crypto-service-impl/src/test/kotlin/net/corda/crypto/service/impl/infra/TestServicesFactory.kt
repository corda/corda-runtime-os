package net.corda.crypto.service.impl.infra

import com.typesafe.config.ConfigFactory
import net.corda.cipher.suite.impl.CipherSchemeMetadataImpl
import net.corda.cipher.suite.impl.DigestServiceImpl
import net.corda.cipher.suite.impl.PlatformDigestServiceImpl
import net.corda.cipher.suite.impl.SignatureVerificationServiceImpl
import net.corda.crypto.cipher.suite.CipherSchemeMetadata
import net.corda.crypto.cipher.suite.ConfigurationSecrets
import net.corda.crypto.cipher.suite.CryptoService
import net.corda.crypto.cipher.suite.CryptoServiceExtensions
import net.corda.crypto.cipher.suite.CryptoServiceProvider
import net.corda.crypto.cipher.suite.GeneratedKey
import net.corda.crypto.cipher.suite.KeyGenerationSpec
import net.corda.crypto.cipher.suite.SharedSecretSpec
import net.corda.crypto.cipher.suite.SignatureVerificationService
import net.corda.crypto.cipher.suite.SigningSpec
import net.corda.crypto.cipher.suite.schemes.KeyScheme
import net.corda.crypto.component.test.utils.TestConfigurationReadService
import net.corda.crypto.config.impl.createCryptoBootstrapParamsMap
import net.corda.crypto.config.impl.createTestCryptoConfig
import net.corda.crypto.core.CryptoConsts
import net.corda.crypto.core.CryptoConsts.SOFT_HSM_ID
import net.corda.crypto.core.aes.KeyCredentials
import net.corda.crypto.core.aes.WrappingKey
import net.corda.crypto.service.CryptoServiceFactory
import net.corda.crypto.service.SigningService
import net.corda.crypto.service.SigningServiceFactory
import net.corda.crypto.service.impl.CryptoServiceFactoryImpl
import net.corda.crypto.service.impl.HSMServiceImpl
import net.corda.crypto.service.impl.SigningServiceFactoryImpl
import net.corda.crypto.service.impl.SigningServiceImpl
import net.corda.crypto.softhsm.SoftCryptoServiceConfig
import net.corda.crypto.softhsm.impl.DefaultSoftPrivateKeyWrapping
import net.corda.crypto.softhsm.impl.SoftCryptoService
import net.corda.crypto.softhsm.impl.TransientSoftKeyMap
import net.corda.crypto.softhsm.impl.TransientSoftWrappingKeyMap
import net.corda.libs.configuration.SmartConfig
import net.corda.libs.configuration.SmartConfigFactory
import net.corda.lifecycle.LifecycleStatus
import net.corda.lifecycle.test.impl.TestLifecycleCoordinatorFactoryImpl
import net.corda.schema.configuration.ConfigKeys
import net.corda.test.util.eventually
import net.corda.v5.crypto.SignatureSpec
import java.util.concurrent.ConcurrentHashMap
import kotlin.test.assertEquals

class TestServicesFactory {
    companion object {
        const val CTX_TRACKING = "ctxTrackingId"
        const val CUSTOM1_HSM_ID = "CUSTOM1"
        const val CUSTOM2_HSM_ID = "CUSTOM2"
    }

    val recordedCryptoContexts = ConcurrentHashMap<String, Map<String, String>>()

    val emptyConfig: SmartConfig =
        SmartConfigFactory.createWithoutSecurityServices().create(ConfigFactory.empty())

    val cryptoConfig: SmartConfig = createTestCryptoConfig(
        KeyCredentials("salt", "passphrase")
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

    val signingKeyStore: TestSigningKeyStore by lazy {
        TestSigningKeyStore(coordinatorFactory).also {
            it.start()
            eventually {
                assertEquals(LifecycleStatus.UP, it.lifecycleCoordinator.status)
            }
        }
    }

    val hsmStore: TestHSMStore by lazy {
        TestHSMStore(coordinatorFactory).also {
            it.start()
            eventually {
                assertEquals(LifecycleStatus.UP, it.lifecycleCoordinator.status)
            }
        }
    }

    val wrappingKeyStore: TestWrappingKeyStore by lazy {
        TestWrappingKeyStore(coordinatorFactory).also {
            it.start()
            eventually {
                assertEquals(LifecycleStatus.UP, it.lifecycleCoordinator.status)
            }
        }
    }

    val signingService: SigningService by lazy {
        SigningServiceImpl(
            signingKeyStore,
            cryptoServiceFactory,
            schemeMetadata
        )
    }

    val signingServiceFactory: SigningServiceFactory by lazy {
        SigningServiceFactoryImpl(
            coordinatorFactory,
            schemeMetadata,
            signingKeyStore,
            cryptoServiceFactory
        )
    }

    val hsmService: HSMServiceImpl by lazy {
        HSMServiceImpl(
            coordinatorFactory,
            configurationReadService,
            hsmStore,
            signingServiceFactory
        ).also {
            it.start()
            eventually {
                assertEquals(LifecycleStatus.UP, it.lifecycleCoordinator.status)
            }
        }
    }

    val cryptoService: CryptoService by lazy {
        val wrappingKeyMap = TransientSoftWrappingKeyMap(
            wrappingKeyStore,
            WrappingKey.generateWrappingKey(schemeMetadata)
        )
        CryptoServiceWrapper(
            SoftCryptoService(
                TransientSoftKeyMap(DefaultSoftPrivateKeyWrapping(wrappingKeyMap)),
                wrappingKeyMap,
                schemeMetadata,
                platformDigest
            ),
            recordedCryptoContexts
        )
    }

    val cryptoServiceFactory: CryptoServiceFactory by lazy {
        CryptoServiceFactoryImpl(
            coordinatorFactory,
            configurationReadService,
            hsmStore,
            object : CryptoServiceProvider<SoftCryptoServiceConfig> {
                override val name: String = CryptoConsts.SOFT_HSM_SERVICE_NAME
                override val configType: Class<SoftCryptoServiceConfig> = SoftCryptoServiceConfig::class.java
                override fun getInstance(
                    config: SoftCryptoServiceConfig,
                    secrets: ConfigurationSecrets
                ): CryptoService = cryptoService
            }
        ).also {
            it.start()
            it.bootstrapConfig(bootstrapConfig)
            eventually {
                assertEquals(LifecycleStatus.UP, it.lifecycleCoordinator.status)
            }
        }
    }

    private class CryptoServiceWrapper(
        private val impl: CryptoService,
        private val recordedCryptoContexts: ConcurrentHashMap<String, Map<String, String>>
    ) : CryptoService {
        override val extensions: List<CryptoServiceExtensions> get() = impl.extensions

        override val supportedSchemes: Map<KeyScheme, List<SignatureSpec>> get() = impl.supportedSchemes

        override fun createWrappingKey(masterKeyAlias: String, failIfExists: Boolean, context: Map<String, String>) {
            if (context.containsKey("ctxTrackingId")) {
                recordedCryptoContexts[context.getValue("ctxTrackingId")] = context
            }
            impl.createWrappingKey(masterKeyAlias, failIfExists, context)
        }

        override fun generateKeyPair(spec: KeyGenerationSpec, context: Map<String, String>): GeneratedKey {
            if (context.containsKey("ctxTrackingId")) {
                recordedCryptoContexts[context.getValue("ctxTrackingId")] = context
            }
            return impl.generateKeyPair(spec, context)
        }

        override fun sign(spec: SigningSpec, data: ByteArray, context: Map<String, String>): ByteArray {
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
