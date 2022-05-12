package net.corda.crypto.service.impl.infra

import com.typesafe.config.ConfigFactory
import net.corda.crypto.core.aes.KeyCredentials
import net.corda.crypto.impl.components.CipherSchemeMetadataImpl
import net.corda.crypto.impl.components.DigestServiceImpl
import net.corda.crypto.impl.components.SignatureVerificationServiceImpl
import net.corda.crypto.impl.config.createDefaultCryptoConfig
import net.corda.crypto.persistence.signing.SigningCachedKey
import net.corda.crypto.service.CryptoServiceFactory
import net.corda.crypto.service.CryptoServiceRef
import net.corda.crypto.service.SoftCryptoServiceProvider
import net.corda.crypto.service.impl.hsm.service.HSMServiceImpl
import net.corda.crypto.service.impl.signing.CryptoServiceFactoryImpl
import net.corda.crypto.service.impl.signing.SigningServiceImpl
import net.corda.crypto.service.impl.hsm.soft.SoftCryptoService
import net.corda.crypto.service.impl.hsm.soft.SoftCryptoServiceProviderImpl
import net.corda.libs.configuration.SmartConfig
import net.corda.libs.configuration.SmartConfigFactory
import net.corda.lifecycle.LifecycleCoordinatorFactory
import net.corda.lifecycle.impl.LifecycleCoordinatorFactoryImpl
import net.corda.lifecycle.impl.registry.LifecycleRegistryImpl
import net.corda.schema.configuration.ConfigKeys
import net.corda.test.util.eventually
import net.corda.v5.cipher.suite.CipherSchemeMetadata
import net.corda.v5.cipher.suite.CryptoService
import net.corda.v5.cipher.suite.GeneratedKey
import net.corda.v5.cipher.suite.KeyGenerationSpec
import net.corda.v5.cipher.suite.SigningSpec
import net.corda.v5.cipher.suite.schemes.SignatureScheme
import net.corda.v5.crypto.DigestService
import net.corda.v5.crypto.SignatureVerificationService
import java.security.PublicKey
import java.util.concurrent.ConcurrentHashMap
import kotlin.test.assertTrue

class TestServicesFactory {
    companion object {
        const val CTX_TRACKING = "ctxTrackingId"
    }

    val wrappingKeyAlias = "wrapping-key-alias"

    val recordedCryptoContexts = ConcurrentHashMap<String, Map<String, String>>()

    private val emptyConfig: SmartConfig =
        SmartConfigFactory.create(ConfigFactory.empty()).create(ConfigFactory.empty())

    private val cryptoConfig: SmartConfig =
        createDefaultCryptoConfig(KeyCredentials("salt", "passphrase"))

    val schemeMetadata: CipherSchemeMetadata = CipherSchemeMetadataImpl()

    val coordinatorFactory: LifecycleCoordinatorFactory = LifecycleCoordinatorFactoryImpl(LifecycleRegistryImpl())

    val digest: DigestService by lazy {
        DigestServiceImpl(schemeMetadata, null)
    }

    val verifier: SignatureVerificationService by lazy {
        SignatureVerificationServiceImpl(schemeMetadata, digest)
    }

    val signingCacheProvider: TestSigningKeyCacheProvider by lazy(LazyThreadSafetyMode.PUBLICATION) {
        TestSigningKeyCacheProvider(coordinatorFactory).also {
            it.start()
            eventually {
                assertTrue(it.isRunning)
            }
        }
    }

    val softCacheProvider: TestSoftCryptoKeyCacheProvider by lazy(LazyThreadSafetyMode.PUBLICATION) {
        TestSoftCryptoKeyCacheProvider(coordinatorFactory).also {
            it.start()
            eventually {
                assertTrue(it.isRunning)
            }
        }
    }

    val hsmService: TestHSMService by lazy(LazyThreadSafetyMode.PUBLICATION) {
        TestHSMService(
            coordinatorFactory,
            HSMServiceImpl(
                cryptoConfig,
                TestHSMCache(),
                schemeMetadata,
                opsProxyClient
            )
        ).also {
            it.start()
            eventually {
                assertTrue(it.isRunning)
            }
        }
    }

    val opsProxyClient: TestCryptoOpsProxyClient by lazy(LazyThreadSafetyMode.PUBLICATION) {
        TestCryptoOpsProxyClient(this).also {
            it.start()
            eventually {
                assertTrue(it.isRunning)
            }
        }
    }

    val hsmCacheProvider: TestHSMCacheProvider by lazy(LazyThreadSafetyMode.PUBLICATION) {
        TestHSMCacheProvider(this).also {
            it.start()
            eventually {
                assertTrue(it.isRunning)
            }
        }
    }

    val softCryptoKeyCacheProvider: SoftCryptoServiceProvider by lazy(LazyThreadSafetyMode.PUBLICATION) {
        SoftCryptoServiceProviderImpl(
            coordinatorFactory,
            schemeMetadata,
            digest,
            softCacheProvider
        ).also {
            it.start()
            eventually {
                assertTrue(it.isRunning)
            }
        }
    }

    val hsmCache: TestHSMCache by lazy(LazyThreadSafetyMode.PUBLICATION) {
        hsmCacheProvider.getInstance() as TestHSMCache
    }

    private val signingCache: TestSigningKeyCache by lazy(LazyThreadSafetyMode.PUBLICATION) {
        signingCacheProvider.getInstance() as TestSigningKeyCache
    }

    val softCache: TestSoftCryptoKeyCache by lazy(LazyThreadSafetyMode.PUBLICATION) {
        softCacheProvider.getInstance() as TestSoftCryptoKeyCache
    }

    val cryptoService: CryptoService by lazy(LazyThreadSafetyMode.PUBLICATION) {
        CryptoServiceWrapper(
            SoftCryptoService(
                cache = softCache,
                schemeMetadata = schemeMetadata,
                digestService = digest
            ).also { it.createWrappingKey(wrappingKeyAlias, true, emptyMap()) },
            recordedCryptoContexts
        )
    }

    val readService: TestConfigurationReadService by lazy(LazyThreadSafetyMode.PUBLICATION) {
        createConfigurationReadService()
    }

    fun createCryptoServiceFactory() =
        CryptoServiceFactoryImpl(
            coordinatorFactory,
            readService,
            hsmService,
            listOf(
                softCryptoKeyCacheProvider
            )
        ).also {
            it.start()
            eventually {
                assertTrue(it.isRunning)
            }
        }

    private fun createConfigurationReadService(
        configUpdates: List<Pair<String, SmartConfig>> = listOf(
            ConfigKeys.BOOT_CONFIG to emptyConfig,
            ConfigKeys.MESSAGING_CONFIG to emptyConfig,
            ConfigKeys.CRYPTO_CONFIG to cryptoConfig
        )
    ): TestConfigurationReadService =
        TestConfigurationReadService(
            coordinatorFactory,
            configUpdates
        ).also {
            it.start()
            eventually {
                assertTrue(it.isRunning)
            }
        }

    fun createSigningService(
        effectiveWrappingKeyAlias: String = wrappingKeyAlias
    ) =
        SigningServiceImpl(
            cache = signingCache,
            cryptoServiceFactory = object : CryptoServiceFactory {
                override fun getInstance(tenantId: String, category: String): CryptoServiceRef {
                    check(isRunning) {
                        "The provider is in invalid state."
                    }
                    return CryptoServiceRef(
                        tenantId = tenantId,
                        category = category,
                        masterKeyAlias = effectiveWrappingKeyAlias,
                        aliasSecret = null,
                        instance = cryptoService
                    )
                }

                override fun getInstance(configId: String): CryptoService = cryptoService

                override var isRunning: Boolean = false
                    private set

                override fun start() {
                    isRunning = true
                }

                override fun stop() {
                    isRunning = false
                }
            }.also { it.start() },
            schemeMetadata = schemeMetadata
        )

    fun getSigningCachedKey(tenantId: String, publicKey: PublicKey): SigningCachedKey? {
        return signingCache.act(tenantId) { it.find(publicKey) }
    }

    private class CryptoServiceWrapper(
        private val impl: CryptoService,
        private val recordedCryptoContexts: ConcurrentHashMap<String, Map<String, String>>
    ) : CryptoService {
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

        override fun requiresWrappingKey(): Boolean =
            impl.requiresWrappingKey()

        override fun sign(spec: SigningSpec, data: ByteArray, context: Map<String, String>): ByteArray {
            if (context.containsKey("ctxTrackingId")) {
                recordedCryptoContexts[context.getValue("ctxTrackingId")] = context
            }
            return impl.sign(spec, data, context)
        }

        override fun supportedSchemes(): Array<SignatureScheme> =
            impl.supportedSchemes()
    }
}
