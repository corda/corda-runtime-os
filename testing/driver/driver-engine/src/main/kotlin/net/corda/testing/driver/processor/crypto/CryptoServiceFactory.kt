package net.corda.testing.driver.processor.crypto

import java.security.KeyPairGenerator
import java.security.PrivateKey
import java.security.PublicKey
import java.util.Hashtable
import java.util.concurrent.ConcurrentHashMap
import net.corda.crypto.cipher.suite.CipherSchemeMetadata
import net.corda.crypto.cipher.suite.CryptoService
import net.corda.crypto.cipher.suite.GeneratedWrappedKey
import net.corda.crypto.cipher.suite.PlatformDigestService
import net.corda.crypto.core.aes.WrappingKeyImpl
import net.corda.crypto.persistence.WrappingKeyInfo
import net.corda.crypto.softhsm.WrappingRepository
import net.corda.crypto.softhsm.impl.SoftCryptoService
import net.corda.crypto.softhsm.impl.WRAPPING_KEY_ENCODING_VERSION
import net.corda.testing.driver.sandbox.CORDA_MEMBERSHIP_PID
import net.corda.testing.driver.sandbox.DRIVER_SERVICE_FILTER
import net.corda.testing.driver.sandbox.DRIVER_SERVICE_NAME
import net.corda.testing.driver.sandbox.DRIVER_SERVICE_RANKING
import net.corda.testing.driver.sandbox.PrivateKeyService
import net.corda.testing.driver.sandbox.WRAPPING_KEY_ALIAS
import org.osgi.annotation.bundle.Capability
import org.osgi.framework.BundleContext
import org.osgi.framework.Constants.EFFECTIVE_ACTIVE
import org.osgi.framework.Constants.OBJECTCLASS
import org.osgi.framework.Constants.SERVICE_RANKING
import org.osgi.framework.ServiceRegistration
import org.osgi.service.component.annotations.Activate
import org.osgi.service.component.annotations.Component
import org.osgi.service.component.annotations.ConfigurationPolicy.REQUIRE
import org.osgi.service.component.annotations.Deactivate
import org.osgi.service.component.annotations.Reference

@Suppress("unused")
@Capability(
    namespace = "osgi.service",
    attribute = [ "$OBJECTCLASS:List<String>='net.corda.crypto.cipher.suite.CryptoService'" ],
    effective = EFFECTIVE_ACTIVE
)
@Component(
    service = [ PrivateKeyService::class ],
    configurationPid = [ CORDA_MEMBERSHIP_PID ],
    configurationPolicy = REQUIRE,
    immediate = true
)
class CryptoServiceFactory @Activate constructor(
    @Reference(target = DRIVER_SERVICE_FILTER)
    private val wrappingRepository: WrappingRepository,
    @Reference(service = CipherSchemeMetadata::class)
    schemeMetadata: CipherSchemeMetadata,
    @Reference(service = PlatformDigestService::class)
    digestService: PlatformDigestService,
    bundleContext: BundleContext
) : PrivateKeyService {
    private val rootWrappingKey = WrappingKeyImpl.generateWrappingKey(schemeMetadata)
    private val wrappedKeys = ConcurrentHashMap<PublicKey, GeneratedWrappedKey>()
    private val registration: ServiceRegistration<CryptoService>

    init {
        wrappingRepository.saveKey(WRAPPING_KEY_ALIAS,
            WrappingKeyInfo(
                encodingVersion = WRAPPING_KEY_ENCODING_VERSION,
                algorithmName = rootWrappingKey.algorithm,
                keyMaterial = rootWrappingKey.wrap(rootWrappingKey),
                generation = 1,
                parentKeyAlias = WRAPPING_KEY_ALIAS
            )
        )

        val cryptoService = SoftCryptoService(
            wrappingRepositoryFactory = { wrappingRepository },
            schemeMetadata = schemeMetadata,
            defaultUnmanagedWrappingKeyName = WRAPPING_KEY_ALIAS,
            unmanagedWrappingKeys = mapOf(WRAPPING_KEY_ALIAS to rootWrappingKey),
            digestService = digestService,
            wrappingKeyCache = null,
            privateKeyCache = null,
            keyPairGeneratorFactory = KeyPairGenerator::getInstance
        )
        val properties = Hashtable<String, Any?>().also { props ->
            props[SERVICE_RANKING] = DRIVER_SERVICE_RANKING
            props[DRIVER_SERVICE_NAME] = true
        }
        registration = bundleContext.registerService(CryptoService::class.java, cryptoService, properties)
    }

    @Deactivate
    fun done() {
        registration.unregister()
    }

    override fun wrap(alias: String, privateKey: PrivateKey): ByteArray {
        return rootWrappingKey.wrap(privateKey).also { keyMaterial ->
            wrappingRepository.saveKey(alias, WrappingKeyInfo(
                encodingVersion = WRAPPING_KEY_ENCODING_VERSION,
                algorithmName = privateKey.algorithm,
                keyMaterial,
                generation = 1,
                parentKeyAlias = WRAPPING_KEY_ALIAS
            ))
        }
    }

    override fun store(wrappedKey: GeneratedWrappedKey) {
        wrappedKeys.putIfAbsent(wrappedKey.publicKey, wrappedKey)
    }

    override fun fetchFor(publicKey: PublicKey): GeneratedWrappedKey? {
        return wrappedKeys[publicKey]
    }
}
