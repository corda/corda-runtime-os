package net.corda.crypto.softhsm.impl

import java.security.KeyPairGenerator
import java.security.PrivateKey
import java.security.PublicKey
import java.util.concurrent.ConcurrentHashMap
import net.corda.crypto.cipher.suite.CipherSchemeMetadata
import net.corda.crypto.cipher.suite.CryptoService
import net.corda.crypto.cipher.suite.GeneratedWrappedKey
import net.corda.crypto.cipher.suite.PlatformDigestService
import net.corda.crypto.core.aes.WrappingKeyImpl
import net.corda.crypto.persistence.WrappingKeyInfo
import net.corda.crypto.softhsm.WrappingRepository
import net.corda.crypto.softhsm.CryptoServiceProvider
import net.corda.crypto.softhsm.PrivateKeyService
import net.corda.libs.configuration.SmartConfig
import org.osgi.service.component.annotations.Activate
import org.osgi.service.component.annotations.Component
import org.osgi.service.component.annotations.ConfigurationPolicy.REQUIRE
import org.osgi.service.component.annotations.Reference
import org.osgi.service.component.annotations.ReferenceCardinality.OPTIONAL
import org.osgi.service.component.propertytypes.ServiceRanking

@Suppress("unused")
@Component(
    service = [ CryptoServiceProvider::class, PrivateKeyService::class ],
    configurationPid = [ "net.corda.testing.driver.sandbox.LocalIdentity" ],
    configurationPolicy = REQUIRE,
    property = [ "corda.driver:Boolean=true" ]
)
@ServiceRanking(Int.MIN_VALUE)
class DriverCryptoServiceProviderImpl @Activate constructor(
    @Reference(target = "(corda.driver=*)", cardinality = OPTIONAL)
    private val wrappingRepository: WrappingRepository?,
    @Reference(service = CipherSchemeMetadata::class)
    private val schemeMetadata: CipherSchemeMetadata,
    @Reference(service = PlatformDigestService::class)
    private val digestService: PlatformDigestService
) : CryptoServiceProvider, PrivateKeyService {
    private companion object {
        private const val MASTER_KEY_ALIAS = "master"
    }

    private val rootWrappingKey = WrappingKeyImpl.generateWrappingKey(schemeMetadata)
    private val wrappedKeys = ConcurrentHashMap<PublicKey, GeneratedWrappedKey>()

    init {
        wrappingRepository?.saveKey(MASTER_KEY_ALIAS, WrappingKeyInfo(
            encodingVersion = WRAPPING_KEY_ENCODING_VERSION,
            algorithmName = rootWrappingKey.algorithm,
            keyMaterial = rootWrappingKey.wrap(rootWrappingKey),
            generation = 1,
            parentKeyAlias = MASTER_KEY_ALIAS
        ))
    }

    override fun wrap(alias: String, privateKey: PrivateKey): ByteArray {
        return rootWrappingKey.wrap(privateKey).also { keyMaterial ->
            wrappingRepository?.saveKey(alias, WrappingKeyInfo(
                encodingVersion = WRAPPING_KEY_ENCODING_VERSION,
                algorithmName = privateKey.algorithm,
                keyMaterial,
                generation = 1,
                parentKeyAlias = MASTER_KEY_ALIAS
            ))
        }
    }

    override fun store(wrappedKey: GeneratedWrappedKey) {
        wrappedKeys.putIfAbsent(wrappedKey.publicKey, wrappedKey)
    }

    override fun fetchFor(publicKey: PublicKey): GeneratedWrappedKey? {
        return wrappedKeys[publicKey]
    }

    override fun getInstance(config: SmartConfig): CryptoService {
        if (wrappingRepository == null) {
            throw IllegalStateException("No WrappingRepository found")
        }
        return SoftCryptoService(
            wrappingRepositoryFactory = { wrappingRepository },
            schemeMetadata = schemeMetadata,
            defaultUnmanagedWrappingKeyName = MASTER_KEY_ALIAS,
            unmanagedWrappingKeys = mapOf(MASTER_KEY_ALIAS to rootWrappingKey),
            digestService = digestService,
            wrappingKeyCache = null,
            privateKeyCache = null,
            keyPairGeneratorFactory = KeyPairGenerator::getInstance
        )
    }
}
