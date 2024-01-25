package net.corda.crypto.softhsm.impl

import net.corda.cipher.suite.impl.CipherSchemeMetadataImpl
import net.corda.cipher.suite.impl.PlatformDigestServiceImpl
import net.corda.crypto.core.CryptoTenants
import net.corda.crypto.core.aes.WrappingKeyImpl
import net.corda.crypto.persistence.SigningKeyMaterialInfo
import net.corda.crypto.persistence.WrappingKeyInfo
import net.corda.crypto.softhsm.SigningRepository
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
import java.security.InvalidParameterException
import java.security.KeyPairGenerator
import java.security.Provider
import java.util.*
import java.util.concurrent.ConcurrentHashMap
import javax.crypto.AEADBadTagException

private val schemeMetadata = CipherSchemeMetadataImpl()

/* Tests around crypto key rewrapping */
class SoftCryptoServiceRewrapTests {
    companion object {
        private const val rootKeyAlias = "root"
        private const val managedWrappingKey1Alias = "k1"
        private val rootWrappingKey = WrappingKeyImpl.generateWrappingKey(schemeMetadata)
        private val knownWrappingKey = WrappingKeyImpl.generateWrappingKey(schemeMetadata)
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
        private val wrappingKeyCache = makeWrappingKeyCache()
        private val shortHashCache = makeShortHashCache()
        private val signingKeys = mutableMapOf<UUID, MutableList<SigningKeyMaterialInfo>>()
        private val signingRepository: SigningRepository = mock {
            on { getKeyMaterials(anyVararg()) } doAnswer {args ->
                signingKeys[args.arguments[0] as UUID]?.map { it }
            }
            on { saveSigningKeyMaterial(any(), any()) } doAnswer {args ->
                if (signingKeys.containsKey(args.arguments[1] as UUID)) {
                    signingKeys[args.arguments[1] as UUID]?.add(args.arguments[0] as SigningKeyMaterialInfo)
                } else {
                    signingKeys[args.arguments[1] as UUID] = mutableListOf(args.arguments[0] as SigningKeyMaterialInfo)
                }
                null
            }
        }
        private val mockHsmAssociation = mock<HSMAssociationInfo> {
            on { masterKeyAlias } doReturn knownWrappingKeyAlias
        }
        private val cryptoService = SoftCryptoService (
            wrappingRepositoryFactory = {
                tenantWrappingRepository
            },
            schemeMetadata = schemeMetadata,
            defaultUnmanagedWrappingKeyName = rootKeyAlias,
            unmanagedWrappingKeys = mapOf(rootKeyAlias to rootWrappingKey),
            digestService = PlatformDigestServiceImpl(schemeMetadata),
            keyPairGeneratorFactory = { algorithm: String, provider: Provider ->
                KeyPairGenerator.getInstance(algorithm, provider)
            },
            wrappingKeyCache = wrappingKeyCache,
            shortHashCache = shortHashCache,
            signingRepositoryFactory = { signingRepository },
            privateKeyCache = null,
            tenantInfoService = mock {
                on { lookup(eq(CryptoTenants.P2P), any()) } doReturn mockHsmAssociation
            }
        )
    }

    @Test
    fun `rewrapAllManagedKeys successfully rewraps keys`() {
        val keyPairGenerator = KeyPairGenerator.getInstance("EC", BouncyCastleProvider())
        keyPairGenerator.initialize(
            ECNamedCurveTable.getParameterSpec("secp256r1"),
            schemeMetadata.secureRandom
        )

        val wrappingKeyMaterial = WrappingKeyImpl.generateWrappingKey(schemeMetadata)
        val wrappingKey = WrappingKeyInfo(
            1,
            wrappingKeyMaterial.algorithm,
            rootWrappingKey.wrap(wrappingKeyMaterial),
            0,
            "root",
            "wrap"
        )
        tenantWrappingRepository.saveKey(wrappingKey)
        val oldWrappingKey = rootWrappingKey.unwrapWrappingKey(rootWrappingKey.wrap(wrappingKeyMaterial))

        val oldSigningKey = keyPairGenerator.genKeyPair().private
        val keyMaterial = SigningKeyMaterialInfo(
            UUID.randomUUID(),
            knownWrappingKey.wrap(oldSigningKey)
        )
        signingRepository.saveSigningKeyMaterial(keyMaterial, wrappingUUID)
        val originalKeys = signingKeys.keys.toSet()
        cryptoService.rewrapAllSigningKeysWrappedBy(wrappingUUID, tenantId)
        val newKeyId = signingKeys.keys.toSet().minus(originalKeys).single()
        val newWrappingKey = rootWrappingKey.unwrapWrappingKey(tenantWrappingRepository.keys[knownWrappingKeyAlias]!!.keyMaterial)
        val newSigningKeyInfo = signingKeys[newKeyId]!![0]
        val newSigningKey = newWrappingKey.unwrap(newSigningKeyInfo.keyMaterial)
        assertThat(newSigningKey).isEqualTo(oldSigningKey)

        assertThrows<AEADBadTagException> {
            oldWrappingKey.unwrap(newSigningKeyInfo.keyMaterial)
        }
    }

    @Test
    fun `rewrapAllManagedKeys fails if wrapping key doesn't exist`() {
        val tenantWrappingRepository: WrappingRepository = mock {
            on { getKeyById(any()) } doReturn null
        }
        val cryptoService = SoftCryptoService (
            wrappingRepositoryFactory = {
                when (it) {
                    tenantId -> tenantWrappingRepository
                    else -> throw InvalidParameterException(it)
                }
            },
            schemeMetadata = schemeMetadata,
            defaultUnmanagedWrappingKeyName = rootKeyAlias,
            unmanagedWrappingKeys = mapOf(rootKeyAlias to rootWrappingKey),
            digestService = PlatformDigestServiceImpl(schemeMetadata),
            keyPairGeneratorFactory = { algorithm: String, provider: Provider ->
                KeyPairGenerator.getInstance(algorithm, provider)
            },
            wrappingKeyCache = wrappingKeyCache,
            shortHashCache = shortHashCache,
            signingRepositoryFactory = { signingRepository },
            privateKeyCache = null,
            tenantInfoService = mock {
                on { lookup(eq(CryptoTenants.P2P), any()) } doReturn mockHsmAssociation
            }
        )
        val e = assertThrows<IllegalStateException> {
            cryptoService.rewrapAllSigningKeysWrappedBy(UUID.randomUUID(), tenantId)
        }
        assertThat(e.message).contains("Unable to find existing wrapping key with id")
    }

    @Test
    fun `rewrapAllManagedKeys fails when parent key cannot be found`() {
        val cryptoService = SoftCryptoService (
            wrappingRepositoryFactory = {
                when (it) {
                    tenantId -> tenantWrappingRepository
                    else -> throw InvalidParameterException(it)
                }
            },
            schemeMetadata = schemeMetadata,
            defaultUnmanagedWrappingKeyName = rootKeyAlias,
            unmanagedWrappingKeys = mapOf(),
            digestService = PlatformDigestServiceImpl(schemeMetadata),
            keyPairGeneratorFactory = { algorithm: String, provider: Provider ->
                KeyPairGenerator.getInstance(algorithm, provider)
            },
            wrappingKeyCache = wrappingKeyCache,
            shortHashCache = shortHashCache,
            signingRepositoryFactory = { signingRepository },
            privateKeyCache = null,
            tenantInfoService = mock {
                on { lookup(eq(CryptoTenants.P2P), any()) } doReturn mockHsmAssociation
            }
        )
        val e = assertThrows<IllegalStateException> {
            cryptoService.rewrapAllSigningKeysWrappedBy(wrappingUUID, tenantId)
        }
        assertThat(e.message).contains("Unable to find parent key")
    }
}