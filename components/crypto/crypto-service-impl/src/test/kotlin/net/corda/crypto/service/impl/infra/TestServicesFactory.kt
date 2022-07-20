package net.corda.crypto.service.impl.infra

import com.typesafe.config.ConfigFactory
import net.corda.cipher.suite.impl.CipherSchemeMetadataImpl
import net.corda.cipher.suite.impl.DigestServiceImpl
import net.corda.cipher.suite.impl.SignatureVerificationServiceImpl
import net.corda.crypto.component.test.utils.TestConfigurationReadService
import net.corda.crypto.core.aes.KeyCredentials
import net.corda.crypto.impl.config.createDefaultCryptoConfig
import net.corda.crypto.persistence.signing.SigningCachedKey
import net.corda.crypto.service.CryptoServiceFactory
import net.corda.crypto.service.CryptoServiceRef
import net.corda.crypto.service.softhsm.SoftCryptoServiceProvider
import net.corda.crypto.service.impl.hsm.service.HSMServiceImpl
import net.corda.crypto.service.impl.softhsm.SoftCryptoService
import net.corda.crypto.service.impl.softhsm.SoftCryptoServiceProviderImpl
import net.corda.crypto.service.impl.signing.CryptoServiceFactoryImpl
import net.corda.crypto.service.impl.signing.SigningServiceImpl
import net.corda.libs.configuration.SmartConfig
import net.corda.libs.configuration.SmartConfigFactory
import net.corda.lifecycle.test.impl.TestLifecycleCoordinatorFactoryImpl
import net.corda.schema.configuration.ConfigKeys
import net.corda.test.util.eventually
import net.corda.v5.cipher.suite.CipherSchemeMetadata
import net.corda.v5.cipher.suite.CryptoService
import net.corda.v5.cipher.suite.CryptoServiceExtensions
import net.corda.v5.cipher.suite.GeneratedKey
import net.corda.v5.cipher.suite.KeyGenerationSpec
import net.corda.v5.cipher.suite.SharedSecretSpec
import net.corda.v5.cipher.suite.SigningSpec
import net.corda.v5.cipher.suite.schemes.KeyScheme
import net.corda.v5.crypto.DigestService
import net.corda.v5.cipher.suite.SignatureVerificationService
import net.corda.v5.crypto.SignatureSpec
import java.security.PublicKey
import java.util.*
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

    val softHSMSupportedSchemas = SoftCryptoService.produceSupportedSchemes(schemeMetadata).map { it.key.codeName }

    val coordinatorFactory: TestLifecycleCoordinatorFactoryImpl = TestLifecycleCoordinatorFactoryImpl()

    val digest: DigestService by lazy {
        DigestServiceImpl(schemeMetadata, null)
    }

    val verifier: SignatureVerificationService by lazy {
        SignatureVerificationServiceImpl(schemeMetadata, digest)
    }

    val signingKeyStoreProvider: TestSigningKeyStoreProvider by lazy(LazyThreadSafetyMode.PUBLICATION) {
        TestSigningKeyStoreProvider(coordinatorFactory).also {
            it.start()
            eventually {
                assertTrue(it.isRunning)
            }
        }
    }

    val softCryptoKeyStoreProvider: TestSoftCryptoKeyStoreProvider by lazy(LazyThreadSafetyMode.PUBLICATION) {
        TestSoftCryptoKeyStoreProvider(coordinatorFactory).also {
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
                TestHSMStore(),
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

    val hsmCacheProvider: TestHSMStoreProvider by lazy(LazyThreadSafetyMode.PUBLICATION) {
        TestHSMStoreProvider(this).also {
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
            softCryptoKeyStoreProvider
        ).also {
            it.start()
            eventually {
                assertTrue(it.isRunning)
            }
        }
    }

    val hsmCache: TestHSMStore by lazy(LazyThreadSafetyMode.PUBLICATION) {
        hsmCacheProvider.getInstance() as TestHSMStore
    }

    private val signingCache: TestSigningKeyStore by lazy(LazyThreadSafetyMode.PUBLICATION) {
        signingKeyStoreProvider.getInstance() as TestSigningKeyStore
    }

    val softCache: TestWrappingKeyStore by lazy(LazyThreadSafetyMode.PUBLICATION) {
        softCryptoKeyStoreProvider.getInstance() as TestWrappingKeyStore
    }

    val cryptoService: CryptoService by lazy(LazyThreadSafetyMode.PUBLICATION) {
        CryptoServiceWrapper(
            SoftCryptoService(
                store = softCache,
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

    fun createSigningService(effectiveWrappingKeyAlias: String = wrappingKeyAlias) =
        SigningServiceImpl(
            store = signingCache,
            cryptoServiceFactory = object : CryptoServiceFactory {
                override fun getServiceRef(tenantId: String, category: String): CryptoServiceRef {
                    check(isRunning) {
                        "The provider is in invalid state."
                    }
                    return CryptoServiceRef(
                        tenantId = tenantId,
                        category = category,
                        masterKeyAlias = effectiveWrappingKeyAlias,
                        aliasSecret = null,
                        instance = cryptoService,
                        associationId = UUID.randomUUID().toString()
                    )
                }

                override fun getInstance(configId: String): CryptoService {
                    check(isRunning) {
                        "The provider is in invalid state."
                    }
                    return cryptoService
                }

                override fun getInstance(tenantId: String, category: String, associationId: String): CryptoServiceRef {
                    check(isRunning) {
                        "The provider is in invalid state."
                    }
                    return CryptoServiceRef(
                        tenantId = tenantId,
                        category = category,
                        masterKeyAlias = effectiveWrappingKeyAlias,
                        aliasSecret = null,
                        instance = cryptoService,
                        associationId = associationId
                    )
                }

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
