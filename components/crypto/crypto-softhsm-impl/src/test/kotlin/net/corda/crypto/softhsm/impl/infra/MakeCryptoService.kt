package net.corda.crypto.softhsm.impl.infra

import com.github.benmanes.caffeine.cache.Cache
import net.corda.cipher.suite.impl.CipherSchemeMetadataImpl
import net.corda.cipher.suite.impl.PlatformDigestServiceImpl
import net.corda.crypto.cipher.suite.CipherSchemeMetadata
import net.corda.crypto.core.CryptoService
import net.corda.crypto.core.ShortHash
import net.corda.crypto.core.aes.WrappingKey
import net.corda.crypto.core.aes.WrappingKeyImpl
import net.corda.crypto.softhsm.SigningRepository
import net.corda.crypto.softhsm.TenantInfoService
import net.corda.crypto.softhsm.WrappingRepository
import net.corda.crypto.softhsm.impl.SoftCryptoService
import net.corda.v5.crypto.SecureHash
import org.mockito.kotlin.mock
import java.security.KeyPairGenerator
import java.security.PrivateKey
import java.security.Provider
import java.security.PublicKey


@Suppress("LongParameterList")
fun makeSoftCryptoService(
    privateKeyCache: Cache<PublicKey, PrivateKey>? = null,
    wrappingKeyCache: Cache<String, WrappingKey>? = null,
    shortHashCache: Cache<ShortHash, SecureHash>?= null,
    schemeMetadata: CipherSchemeMetadataImpl = CipherSchemeMetadataImpl(),
    rootWrappingKey: WrappingKey = WrappingKeyImpl.generateWrappingKey(schemeMetadata),
    wrappingKeyFactory: (schemeMetadata: CipherSchemeMetadata) -> WrappingKey = { it ->
        WrappingKeyImpl.generateWrappingKey(it)
    },
    wrappingRepository: WrappingRepository = TestWrappingRepository(),
    signingRepository: SigningRepository = TestSigningRepository(),
    tenantInfoService: TenantInfoService = mock()
): CryptoService {
    return SoftCryptoService(
        wrappingRepositoryFactory = { wrappingRepository },
        signingRepositoryFactory = { signingRepository },
        schemeMetadata = schemeMetadata,
        defaultUnmanagedWrappingKeyName = "root",
        unmanagedWrappingKeys = mapOf("root" to rootWrappingKey),
        digestService = PlatformDigestServiceImpl(schemeMetadata),
        wrappingKeyCache = wrappingKeyCache,
        privateKeyCache = privateKeyCache,
        shortHashCache =  shortHashCache,
        keyPairGeneratorFactory = { algorithm: String, provider: Provider ->
            KeyPairGenerator.getInstance(algorithm, provider)
        },
        wrappingKeyFactory = wrappingKeyFactory,
        signingKeyInfoCache =  makeSigningKeyInfoCache(),
        tenantInfoService =  tenantInfoService
    )
}