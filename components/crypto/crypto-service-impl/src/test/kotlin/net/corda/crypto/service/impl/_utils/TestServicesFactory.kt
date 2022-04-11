package net.corda.crypto.service.impl._utils

import com.typesafe.config.ConfigFactory
import net.corda.crypto.impl.components.CipherSchemeMetadataImpl
import net.corda.crypto.impl.components.DigestServiceImpl
import net.corda.crypto.impl.components.SignatureVerificationServiceImpl
import net.corda.crypto.persistence.SigningCachedKey
import net.corda.crypto.service.CryptoServiceFactory
import net.corda.crypto.service.CryptoServiceRef
import net.corda.crypto.service.impl.signing.CryptoServiceFactoryImpl
import net.corda.crypto.service.impl.signing.SigningServiceImpl
import net.corda.crypto.service.impl.soft.SoftCryptoService
import net.corda.crypto.service.impl.soft.SoftCryptoServiceProviderImpl
import net.corda.libs.configuration.SmartConfig
import net.corda.libs.configuration.SmartConfigFactory
import net.corda.lifecycle.impl.LifecycleCoordinatorFactoryImpl
import net.corda.lifecycle.impl.registry.LifecycleRegistryImpl
import net.corda.schema.configuration.ConfigKeys
import net.corda.test.util.eventually
import net.corda.v5.cipher.suite.schemes.SignatureScheme
import java.security.PublicKey
import kotlin.test.assertTrue

class TestServicesFactory {
    val wrappingKeyAlias = "wrapping-key-alias"

    private val passphrase = "PASSPHRASE"

    private val salt = "SALT"

    private val emptyConfig: SmartConfig =
        SmartConfigFactory.create(ConfigFactory.empty()).create(ConfigFactory.empty())

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
        TestHSMRegistration(coordinatorFactory).also {
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
        softCacheProvider.getInstance(passphrase, salt)
    }

    val cryptoService by lazy(LazyThreadSafetyMode.PUBLICATION) {
        SoftCryptoService(
            cache = softCache,
            schemeMetadata = schemeMetadata,
            digestService = digest
        ).also { it.createWrappingKey(wrappingKeyAlias, true, emptyMap()) }
    }

    fun createCryptoServiceFactory() =
        CryptoServiceFactoryImpl(
            coordinatorFactory,
            registration,
            schemeMetadata,
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
            ConfigKeys.MESSAGING_CONFIG to emptyConfig
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
        signatureScheme: SignatureScheme,
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
                        signatureScheme = signatureScheme,
                        masterKeyAlias = effectiveWrappingKeyAlias,
                        aliasSecret = null,
                        instance = cryptoService
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
}
