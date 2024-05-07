package net.corda.crypto.softhsm.impl.infra

import com.github.benmanes.caffeine.cache.Cache
import net.corda.cipher.suite.impl.CipherSchemeMetadataImpl
import net.corda.cipher.suite.impl.PlatformDigestServiceImpl
import net.corda.crypto.cipher.suite.CipherSchemeMetadata
import net.corda.crypto.core.CryptoService
import net.corda.crypto.core.SigningKeyInfo
import net.corda.crypto.core.aes.WrappingKey
import net.corda.crypto.core.aes.WrappingKeyImpl
import net.corda.crypto.softhsm.SigningRepository
import net.corda.crypto.softhsm.TenantInfoService
import net.corda.crypto.softhsm.WrappingRepository
import net.corda.crypto.softhsm.impl.ShortHashCacheKey
import net.corda.crypto.softhsm.impl.SoftCryptoService
import org.mockito.kotlin.mock
import java.security.KeyPairGenerator
import java.security.PrivateKey
import java.security.Provider
import java.security.PublicKey


@Suppress("LongParameterList")
fun makeSoftCryptoService(
    privateKeyCache: Cache<PublicKey, PrivateKey>? = null,
    wrappingKeyCache: Cache<String, WrappingKey>? = null,
    shortHashCache: Cache<ShortHashCacheKey, SigningKeyInfo>? = null,
    schemeMetadata: CipherSchemeMetadataImpl = CipherSchemeMetadataImpl(),
    rootWrappingKey: WrappingKey = WrappingKeyImpl.generateWrappingKey(schemeMetadata),
    rootWrappingKey2: WrappingKey? = null,
    wrappingKeyFactory: (schemeMetadata: CipherSchemeMetadata) -> WrappingKey = { it ->
        WrappingKeyImpl.generateWrappingKey(it)
    },
    wrappingRepository: WrappingRepository = TestWrappingRepository(),
    signingRepository: SigningRepository = TestSigningRepository(),
    tenantInfoService: TenantInfoService = mock(),
): CryptoService {
    return SoftCryptoService(
        wrappingRepositoryFactory = { wrappingRepository },
        signingRepositoryFactory = { signingRepository },
        schemeMetadata = schemeMetadata,
        defaultUnmanagedWrappingKeyName = "root",
        unmanagedWrappingKeys = mapOf("root" to rootWrappingKey)
                + (if (rootWrappingKey2 != null) mapOf("root2" to rootWrappingKey2) else emptyMap()),
        digestService = PlatformDigestServiceImpl(schemeMetadata),
        wrappingKeyCache = wrappingKeyCache,
        privateKeyCache = privateKeyCache,
        shortHashCache =  shortHashCache ?: makeShortHashCache(),
        keyPairGeneratorFactory = { algorithm: String, provider: Provider ->
            KeyPairGenerator.getInstance(algorithm, provider)
        },
        wrappingKeyFactory = wrappingKeyFactory,
        clusterDbInfoService =  tenantInfoService
    )
}
