package net.corda.crypto.softhsm.impl

import com.github.benmanes.caffeine.cache.Caffeine
import java.security.KeyPairGenerator
import java.security.PrivateKey
import java.security.Provider
import net.corda.cache.caffeine.CacheFactoryImpl
import net.corda.crypto.cipher.suite.CipherSchemeMetadata
import net.corda.crypto.cipher.suite.CryptoService
import net.corda.crypto.cipher.suite.PlatformDigestService
import net.corda.crypto.core.aes.WrappingKeyImpl
import net.corda.crypto.persistence.WrappingKeyInfo
import net.corda.crypto.softhsm.CryptoServiceProvider
import net.corda.crypto.softhsm.PrivateKeyService
import net.corda.crypto.softhsm.WrappingRepository
import net.corda.libs.configuration.SmartConfig
import org.osgi.service.component.annotations.Activate
import org.osgi.service.component.annotations.Component
import org.osgi.service.component.annotations.Reference
import org.osgi.service.component.annotations.ReferenceCardinality.OPTIONAL
import org.osgi.service.component.propertytypes.ServiceRanking

@Suppress("unused")
@Component(service = [ CryptoServiceProvider::class, PrivateKeyService::class ], property = [ "corda.driver:Boolean=true" ])
@ServiceRanking(Int.MIN_VALUE)
class DriverCryptoServiceProviderImpl @Activate constructor(
    @Reference(target = "(corda.driver=*)", service = WrappingRepository::class, cardinality = OPTIONAL)
    private val wrappingRepository: WrappingRepository?,
    @Reference(service = CipherSchemeMetadata::class)
    private val schemeMetadata: CipherSchemeMetadata,
    @Reference(service = PlatformDigestService::class)
    private val digestService: PlatformDigestService
) : CryptoServiceProvider, PrivateKeyService {
    private val rootWrappingKey = WrappingKeyImpl.generateWrappingKey(schemeMetadata)

    init {
        wrappingRepository?.saveKey("master", WrappingKeyInfo(
            encodingVersion = WRAPPING_KEY_ENCODING_VERSION,
            algorithmName = rootWrappingKey.algorithm,
            keyMaterial = rootWrappingKey.wrap(rootWrappingKey)
        ))
    }

    override fun wrap(alias: String, privateKey: PrivateKey): ByteArray {
        return rootWrappingKey.wrap(privateKey).also { keyMaterial ->
            wrappingRepository?.saveKey(alias, WrappingKeyInfo(
                encodingVersion = WRAPPING_KEY_ENCODING_VERSION,
                algorithmName = privateKey.algorithm,
                keyMaterial
            ))
        }
    }

    override fun getInstance(config: SmartConfig): CryptoService {
        if (wrappingRepository == null) {
            throw IllegalStateException("No WrappingRepository found")
        }
        val cacheFactoryImpl = CacheFactoryImpl()
        return SoftCryptoService(
            wrappingRepositoryFactory = { wrappingRepository },
            schemeMetadata = schemeMetadata,
            digestService = digestService,
            rootWrappingKey = rootWrappingKey,
            wrappingKeyCache = cacheFactoryImpl.build("no-caching", Caffeine.newBuilder().maximumSize(0)),
            privateKeyCache = cacheFactoryImpl.build("no-caching", Caffeine.newBuilder().maximumSize(0)),
            keyPairGeneratorFactory = { algorithm: String, provider: Provider ->
                KeyPairGenerator.getInstance(algorithm, provider)
            },
            wrappingKeyFactory = WrappingKeyImpl::generateWrappingKey
        )
    }
}
