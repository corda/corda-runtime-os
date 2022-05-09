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
import java.security.PublicKey
import kotlin.test.assertTrue

class TestServicesFactory {
    val wrappingKeyAlias = "wrapping-key-alias"

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

    val cryptoService by lazy(LazyThreadSafetyMode.PUBLICATION) {
        SoftCryptoService(
            cache = softCache,
            schemeMetadata = schemeMetadata,
            digestService = digest
        ).also { it.createWrappingKey(wrappingKeyAlias, true, emptyMap()) }
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
}
