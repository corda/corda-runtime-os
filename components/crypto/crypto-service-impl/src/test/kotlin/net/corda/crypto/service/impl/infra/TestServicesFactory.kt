package net.corda.crypto.service.impl.infra

import com.typesafe.config.ConfigFactory
import net.corda.cipher.suite.impl.CipherSchemeMetadataImpl
import net.corda.cipher.suite.impl.DigestServiceImpl
import net.corda.cipher.suite.impl.SignatureVerificationServiceImpl
import net.corda.crypto.component.test.utils.TestConfigurationReadService
import net.corda.crypto.config.impl.createDefaultCryptoConfig
import net.corda.crypto.core.aes.KeyCredentials
import net.corda.crypto.core.aes.WrappingKey
import net.corda.crypto.service.impl.HSMServiceImpl
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
import net.corda.v5.cipher.suite.CipherSchemeMetadata
import net.corda.v5.cipher.suite.CryptoService
import net.corda.v5.cipher.suite.CryptoServiceExtensions
import net.corda.v5.cipher.suite.GeneratedKey
import net.corda.v5.cipher.suite.KeyGenerationSpec
import net.corda.v5.cipher.suite.SharedSecretSpec
import net.corda.v5.cipher.suite.SignatureVerificationService
import net.corda.v5.cipher.suite.SigningSpec
import net.corda.v5.cipher.suite.schemes.KeyScheme
import net.corda.v5.crypto.DigestService
import net.corda.v5.crypto.SignatureSpec
import java.util.concurrent.ConcurrentHashMap
import kotlin.test.assertEquals

class TestServicesFactory {
    companion object {
        const val CTX_TRACKING = "ctxTrackingId"
    }

    val wrappingKeyAlias = "wrapping-key-alias"

    val recordedCryptoContexts = ConcurrentHashMap<String, Map<String, String>>()

    val emptyConfig: SmartConfig =
        SmartConfigFactory.create(ConfigFactory.empty()).create(ConfigFactory.empty())

    val cryptoConfig: SmartConfig =
        createDefaultCryptoConfig(KeyCredentials("salt", "passphrase"))

    val schemeMetadata: CipherSchemeMetadata = CipherSchemeMetadataImpl()

    val coordinatorFactory: TestLifecycleCoordinatorFactoryImpl = TestLifecycleCoordinatorFactoryImpl()

    val digest: DigestService by lazy {
        DigestServiceImpl(schemeMetadata, null)
    }

    val verifier: SignatureVerificationService by lazy {
        SignatureVerificationServiceImpl(schemeMetadata, digest)
    }

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
        TestHSMStore(coordinatorFactory, schemeMetadata).also {
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

    val hsmService: HSMServiceImpl by lazy {
        HSMServiceImpl(
            coordinatorFactory,
            configurationReadService,
            hsmStore,
            opsProxyClient
        ).also {
            it.start()
            eventually {
                assertEquals(LifecycleStatus.UP, it.lifecycleCoordinator.status)
            }
        }
    }

    val opsProxyClient: TestCryptoOpsProxyClient by lazy {
        TestCryptoOpsProxyClient(this).also {
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
                digest
            ),
            recordedCryptoContexts
        )
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
