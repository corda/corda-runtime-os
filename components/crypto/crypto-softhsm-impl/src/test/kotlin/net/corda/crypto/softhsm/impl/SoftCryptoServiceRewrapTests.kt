package net.corda.crypto.softhsm.impl

import com.github.benmanes.caffeine.cache.Cache
import net.corda.cipher.suite.impl.CipherSchemeMetadataImpl
import net.corda.cipher.suite.impl.PlatformDigestServiceImpl
import net.corda.crypto.cipher.suite.CipherSchemeMetadata
import net.corda.crypto.cipher.suite.PlatformDigestService
import net.corda.crypto.core.CryptoTenants
import net.corda.crypto.core.SigningKeyInfo
import net.corda.crypto.core.aes.WrappingKey
import net.corda.crypto.core.aes.WrappingKeyImpl
import net.corda.crypto.persistence.SigningKeyMaterialInfo
import net.corda.crypto.persistence.WrappingKeyInfo
import net.corda.crypto.softhsm.SigningRepository
import net.corda.crypto.softhsm.TenantInfoService
import net.corda.crypto.softhsm.WrappingRepository
import net.corda.crypto.softhsm.impl.infra.TestWrappingRepository
import net.corda.crypto.softhsm.impl.infra.makeShortHashCache
import net.corda.crypto.softhsm.impl.infra.makeWrappingKeyCache
import net.corda.data.crypto.wire.hsm.HSMAssociationInfo
import org.assertj.core.api.Assertions.assertThat
import org.bouncycastle.jce.ECNamedCurveTable
import org.bouncycastle.jce.provider.BouncyCastleProvider
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.mockito.kotlin.any
import org.mockito.kotlin.anyVararg
import org.mockito.kotlin.doAnswer
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import java.lang.IllegalStateException
import java.security.KeyPairGenerator
import java.security.PrivateKey
import java.security.Provider
import java.security.PublicKey
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import javax.crypto.AEADBadTagException

private val defaultSchemeMetadata = CipherSchemeMetadataImpl()

/* Tests around crypto key rewrapping */
class SoftCryptoServiceRewrapTests {
    companion object {
        private const val rootKeyAlias = "root"
        private const val managedWrappingKey1Alias = "k1"
        private val rootWrappingKey = WrappingKeyImpl.generateWrappingKey(defaultSchemeMetadata)
        private val knownWrappingKey = WrappingKeyImpl.generateWrappingKey(defaultSchemeMetadata)
        private val knownWrappingKeyMaterial = rootWrappingKey.wrap(knownWrappingKey)
        private val knownWrappingKeyAlias = managedWrappingKey1Alias
        private val wrappingUUID = UUID.randomUUID()

        private val tenantId = UUID.randomUUID().toString()
        private val tenantWrappingRepository = TestWrappingRepository(
            ConcurrentHashMap(
                listOf(
                    knownWrappingKeyAlias to WrappingKeyInfo(
                        WRAPPING_KEY_ENCODING_VERSION,
                        knownWrappingKey.algorithm,
                        knownWrappingKeyMaterial,
                        1,
                        rootKeyAlias,
                        managedWrappingKey1Alias
                    )
                ).toMap()
            )
        )
        private val signingKeyMaterialMap = mutableMapOf<UUID, MutableList<SigningKeyMaterialInfo>>()
        private val signingRepository: SigningRepository = mock {
            on { getKeyMaterials(anyVararg()) } doAnswer {args ->
                signingKeyMaterialMap[args.arguments[0] as UUID]?.map { it }
            }
            on { saveSigningKeyMaterial(any(), any()) } doAnswer {args ->
                if (signingKeyMaterialMap.containsKey(args.arguments[1] as UUID)) {
                    signingKeyMaterialMap[args.arguments[1] as UUID]?.add(args.arguments[0] as SigningKeyMaterialInfo)
                } else {
                    signingKeyMaterialMap[args.arguments[1] as UUID] = mutableListOf(args.arguments[0] as SigningKeyMaterialInfo)
                }
                null
            }
        }
        private val mockHsmAssociation = mock<HSMAssociationInfo> {
            on { masterKeyAlias } doReturn knownWrappingKeyAlias
        }

        @Suppress("LongParameterList")
        private fun createCryptoService(
            wrappingRepositoryFactory: (String) -> WrappingRepository = { tenantWrappingRepository },
            schemeMetadata: CipherSchemeMetadata = defaultSchemeMetadata,
            defaultUnmanagedWrappingKeyName: String = rootKeyAlias,
            unmanagedWrappingKeys: Map<String, WrappingKey> = mapOf(rootKeyAlias to rootWrappingKey),
            digestService: PlatformDigestService = PlatformDigestServiceImpl(defaultSchemeMetadata),
            keyPairGeneratorFactory: (String, Provider) -> KeyPairGenerator = {
                algorithm: String, provider: Provider ->
                KeyPairGenerator.getInstance(algorithm, provider)
            },
            wrappingKeyCache: Cache<String, WrappingKey> = makeWrappingKeyCache(),
            shortHashCache: Cache<ShortHashCacheKey, SigningKeyInfo> = makeShortHashCache(),
            signingRepositoryFactory: (String) -> SigningRepository = { signingRepository },
            privateKeyCache: Cache<PublicKey, PrivateKey>? = null,
            tenantInfoService: TenantInfoService = mock {
                on { lookup(eq(CryptoTenants.P2P), any()) } doReturn mockHsmAssociation
            }
        ) = SoftCryptoService (
            wrappingRepositoryFactory = wrappingRepositoryFactory,
            schemeMetadata = schemeMetadata,
            defaultUnmanagedWrappingKeyName = defaultUnmanagedWrappingKeyName,
            unmanagedWrappingKeys = unmanagedWrappingKeys,
            digestService = digestService,
            keyPairGeneratorFactory = keyPairGeneratorFactory,
            wrappingKeyCache = wrappingKeyCache,
            shortHashCache = shortHashCache,
            signingRepositoryFactory = signingRepositoryFactory,
            privateKeyCache = privateKeyCache,
            tenantInfoService = tenantInfoService
        )
    }

    @Test
    fun `rewrapAllManagedKeys successfully rewraps keys`() {
        val keyPairGenerator = KeyPairGenerator.getInstance("EC", BouncyCastleProvider())
        keyPairGenerator.initialize(
            ECNamedCurveTable.getParameterSpec("secp256r1"),
            defaultSchemeMetadata.secureRandom
        )

        val oldWrappingKey = WrappingKeyImpl.generateWrappingKey(defaultSchemeMetadata)
        val oldWrappingKeyInfo = WrappingKeyInfo(
            1,
            oldWrappingKey.algorithm,
            rootWrappingKey.wrap(oldWrappingKey),
            0,
            "root",
            "wrap"
        )
        tenantWrappingRepository.saveKey(oldWrappingKeyInfo)

        val oldSigningKey = keyPairGenerator.genKeyPair().private
        val oldSigningKeyMaterialInfo = SigningKeyMaterialInfo(
            UUID.randomUUID(),
            knownWrappingKey.wrap(oldSigningKey)
        )
        signingRepository.saveSigningKeyMaterial(oldSigningKeyMaterialInfo, wrappingUUID)
        val originalSigningKeyMaterialIds = signingKeyMaterialMap.keys.toSet()
        val cryptoService = createCryptoService()
        cryptoService.rewrapAllSigningKeysWrappedBy(wrappingUUID, tenantId)
        val newSigningKeyMaterialId = signingKeyMaterialMap.keys.toSet().minus(originalSigningKeyMaterialIds).single()
        val newWrappingKey = rootWrappingKey.unwrapWrappingKey(tenantWrappingRepository.keys[knownWrappingKeyAlias]!!.keyMaterial)
        val newSigningKeyMaterialInfo = signingKeyMaterialMap[newSigningKeyMaterialId]!![0]
        val newSigningKeyMaterialUnwrapped = newWrappingKey.unwrap(newSigningKeyMaterialInfo.keyMaterial)
        assertThat(newSigningKeyMaterialUnwrapped).isEqualTo(oldSigningKey)

        assertThrows<AEADBadTagException> {
            oldWrappingKey.unwrap(newSigningKeyMaterialInfo.keyMaterial)
        }
    }

    @Test
    fun `rewrapAllManagedKeys fails if wrapping key doesn't exist`() {
        val tenantWrappingRepository: WrappingRepository = mock {
            on { getKeyById(any()) } doReturn null
        }
        val cryptoService = createCryptoService(wrappingRepositoryFactory = {
            tenantWrappingRepository
        })
        assertThrows<IllegalStateException> {
            cryptoService.rewrapAllSigningKeysWrappedBy(UUID.randomUUID(), tenantId)
        }
    }

    @Test
    fun `rewrapAllManagedKeys fails when parent key cannot be found`() {
        val cryptoService = createCryptoService(unmanagedWrappingKeys = mapOf())
        assertThrows<IllegalStateException> {
            cryptoService.rewrapAllSigningKeysWrappedBy(wrappingUUID, tenantId)
        }
    }
}