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
import net.corda.crypto.service.impl.signing.CryptoServiceFactoryImpl
import net.corda.crypto.service.impl.signing.SigningServiceImpl
import net.corda.crypto.service.impl.hsm.soft.SoftCryptoService
import net.corda.crypto.service.impl.hsm.soft.SoftCryptoServiceProviderImpl
import net.corda.libs.configuration.SmartConfig
import net.corda.libs.configuration.SmartConfigFactory
import net.corda.lifecycle.impl.LifecycleCoordinatorFactoryImpl
import net.corda.lifecycle.impl.registry.LifecycleRegistryImpl
import net.corda.schema.configuration.ConfigKeys
import net.corda.test.util.eventually
import net.corda.v5.cipher.suite.CryptoService
import net.corda.v5.cipher.suite.GeneratedKey
import net.corda.v5.cipher.suite.KeyGenerationSpec
import net.corda.v5.cipher.suite.SigningSpec
import net.corda.v5.cipher.suite.schemes.SignatureScheme
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

    private val cryptoConfig = createDefaultCryptoConfig(KeyCredentials("salt", "passphrase"))

    val schemeMetadata = CipherSchemeMetadataImpl()

    val coordinatorFactory = LifecycleCoordinatorFactoryImpl(LifecycleRegistryImpl())

    val digest by lazy {
        DigestServiceImpl(schemeMetadata, null)
    }

    val verifier by lazy {
        SignatureVerificationServiceImpl(schemeMetadata, digest)
    }

    val signingCacheProvider by lazy(LazyThreadSafetyMode.PUBLICATION) {
        TestSigningKeyCacheProvider(coordinatorFactory).also {
            it.start()
            eventually {
                assertTrue(it.isRunning)
            }
        }
    }

    val softCacheProvider by lazy(LazyThreadSafetyMode.PUBLICATION) {
        TestSoftCryptoKeyCacheProvider(coordinatorFactory).also {
            it.start()
            eventually {
                assertTrue(it.isRunning)
            }
        }
    }

    val registration by lazy(LazyThreadSafetyMode.PUBLICATION) {
        TestHSMService(coordinatorFactory, schemeMetadata).also {
            it.start()
            eventually {
                assertTrue(it.isRunning)
            }
        }
    }

    val softCryptoKeyCacheProvider by lazy(LazyThreadSafetyMode.PUBLICATION) {
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

    private val signingCache by lazy(LazyThreadSafetyMode.PUBLICATION) {
        signingCacheProvider.getInstance()
    }

    private val softCache by lazy(LazyThreadSafetyMode.PUBLICATION) {
        softCacheProvider.getInstance()
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

    val readService by lazy(LazyThreadSafetyMode.PUBLICATION) {
        createConfigurationReadService()
    }

    fun createCryptoServiceFactory() =
        CryptoServiceFactoryImpl(
            coordinatorFactory,
            readService,
            registration,
            listOf(
                softCryptoKeyCacheProvider
            )
        ).also {
            it.start()
            eventually {
                assertTrue(it.isRunning)
            }
        }

    fun createConfigurationReadService(
        configUpdates: List<Pair<String, SmartConfig>> = listOf(
            ConfigKeys.BOOT_CONFIG to emptyConfig,
            ConfigKeys.MESSAGING_CONFIG to emptyConfig,
            ConfigKeys.CRYPTO_CONFIG to cryptoConfig
        )
    ) =
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
