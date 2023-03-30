package repository

import net.corda.cipher.suite.impl.CipherSchemeMetadataImpl
import net.corda.cipher.suite.impl.PlatformDigestServiceImpl
import net.corda.crypto.cipher.suite.GeneratedPublicKey
import net.corda.crypto.cipher.suite.GeneratedWrappedKey
import net.corda.crypto.cipher.suite.schemes.KeyScheme
import net.corda.crypto.cipher.suite.schemes.KeySchemeCapability
import net.corda.crypto.core.ShortHash
import net.corda.crypto.core.fullPublicKeyIdFromBytes
import net.corda.crypto.core.parseSecureHash
import net.corda.crypto.core.publicKeyIdFromBytes
import net.corda.crypto.persistence.SigningKeyInfo
import net.corda.crypto.persistence.SigningKeyStatus
import net.corda.crypto.persistence.SigningPublicKeySaveContext
import net.corda.crypto.persistence.SigningWrappedKeySaveContext
import net.corda.crypto.persistence.WrappingKeyInfo
import net.corda.crypto.persistence.db.model.WrappingKeyEntity
import net.corda.crypto.softhsm.impl.SigningRepositoryImpl
import net.corda.crypto.softhsm.impl.toDto
import net.corda.crypto.testkit.SecureHashUtils
import net.corda.layeredpropertymap.LayeredPropertyMapFactory
import net.corda.orm.utils.transaction
import net.corda.orm.utils.use
import org.assertj.core.api.Assertions.assertThat
import org.bouncycastle.asn1.x509.AlgorithmIdentifier
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.MethodSource
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import java.security.PublicKey
import java.security.spec.AlgorithmParameterSpec
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.util.UUID
import javax.persistence.EntityManagerFactory

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class SigningRepositoryTest : CryptoRepositoryTest() {
    private val cipherSchemeMetadata = CipherSchemeMetadataImpl()
    private val digestService = PlatformDigestServiceImpl(cipherSchemeMetadata)

    private fun createNewPubKeyInfo(): SigningKeyInfo {
        val unique = UUID.randomUUID().toString()
        val key = SecureHashUtils.randomBytes()
        val keyId = publicKeyIdFromBytes(key)
        val fullKey = fullPublicKeyIdFromBytes(key, digestService)
        return SigningKeyInfo(
            id = ShortHash.parse(keyId),
            fullId = parseSecureHash(fullKey),
            tenantId = "t-$unique".take(12),
            category = "c-$unique",
            alias = "a-$unique",
            hsmAlias = "ha-$unique",
            publicKey = key,
            keyMaterial = null,
            schemeCodeName = "FOO",
            masterKeyAlias = null,
            externalId = "e-$unique",
            encodingVersion = null,
            timestamp = Instant.now(),
            hsmId = "hi-$unique".take(36),
            status = SigningKeyStatus.NORMAL
        )
    }

    private fun createPrivateKeyInfo(): SigningKeyInfo {
        val privKey = SecureHashUtils.randomBytes()
        return createNewPubKeyInfo().copy(
            keyMaterial = privKey,
            encodingVersion = 1,
            hsmAlias = null,
            masterKeyAlias = "Domination's the name of the game"
        )
    }

    private fun createGeneratedPublicKey(info: SigningKeyInfo): GeneratedPublicKey {
        val pubKey = mock<PublicKey> {
            on { encoded } doReturn(info.publicKey)
        }
        return GeneratedPublicKey(pubKey, info.hsmAlias!!)
    }

    private fun createGeneratedWrappedKey(info: SigningKeyInfo): GeneratedWrappedKey {
        val pubKey = mock<PublicKey> {
            on { encoded } doReturn(info.publicKey)
        }
        return GeneratedWrappedKey(pubKey, info.keyMaterial!!, info.encodingVersion!!)
    }

    private fun createKeyScheme(info: SigningKeyInfo) =
        KeyScheme(
            codeName = info.schemeCodeName,
            algorithmOIDs = listOf(mock<AlgorithmIdentifier>()),
            providerName = "foo",
            algorithmName = "bar",
            algSpec = mock< AlgorithmParameterSpec>(),
            keySize = 456,
            capabilities = setOf(KeySchemeCapability.SIGN)
        )

    private fun createLayeredPropertyMapFactory(info: SigningKeyInfo): LayeredPropertyMapFactory {
        println(info)
        return mock<LayeredPropertyMapFactory>()
    }

    private fun saveWrappingKey(emf: EntityManagerFactory, alias: String): WrappingKeyInfo {
        return emf.createEntityManager().use { em ->
            em.transaction {
                it.merge(
                    WrappingKeyEntity(
                        id = UUID.randomUUID(),
                        generation = 1,
                        alias = alias,
                        created = Instant.now(),
                        rotationDate = LocalDate.parse("9999-12-31").atStartOfDay().toInstant(ZoneOffset.UTC),
                        encodingVersion = 1,
                        algorithmName = "foo",
                        keyMaterial = SecureHashUtils.randomBytes(),
                        isParentKeyManaged = false,
                        parentKeyReference = "root",
                    )
                )
            }
        }.toDto()
    }

    @ParameterizedTest
    @MethodSource("emfs")
    fun `savePublicKey and find`(emf: EntityManagerFactory) {
        val info = createNewPubKeyInfo()
        val pubKey = createGeneratedPublicKey(info)
        val ctx = SigningPublicKeySaveContext(
            key = pubKey,
            alias = info.alias,
            category = info.category,
            keyScheme = createKeyScheme(info),
            externalId = info.externalId,
            hsmId = info.hsmId,
        )

        val repo = SigningRepositoryImpl(
            emf,
            info.tenantId,
            cipherSchemeMetadata,
            digestService,
            createLayeredPropertyMapFactory(info),
        )

        repo.savePublicKey(ctx)

        val found = repo.findKey(pubKey.publicKey)

        assertThat(found)
            .usingRecursiveComparison().ignoringFields("timestamp")
            .isEqualTo(info)
    }

    @ParameterizedTest
    @MethodSource("emfs")
    fun savePrivateKey(emf: EntityManagerFactory) {
        val info = createPrivateKeyInfo()
        val pubKey = createGeneratedWrappedKey(info)

        saveWrappingKey(emf, info.masterKeyAlias!!)

        val ctx = SigningWrappedKeySaveContext(
            key = pubKey,
            masterKeyAlias = info.masterKeyAlias,
            externalId = info.externalId,
            alias = info.alias,
            category = info.category,
            keyScheme = createKeyScheme(info),
            hsmId = info.hsmId,
        )


        val repo = SigningRepositoryImpl(
            emf,
            info.tenantId,
            cipherSchemeMetadata,
            digestService,
            createLayeredPropertyMapFactory(info),
        )

        repo.savePrivateKey(ctx)

        val found = repo.findKey(pubKey.publicKey)

        assertThat(found)
            .usingRecursiveComparison().ignoringFields("timestamp")
            .isEqualTo(info)
    }

//    @ParameterizedTest
//    @MethodSource("emfs")
//    fun `findKey by alias`(emf: EntityManagerFactory) {
//
//    }
//
//    @ParameterizedTest
//    @MethodSource("emfs")
//    fun `findKey by public key`(emf: EntityManagerFactory) {
//
//    }
//
//    @ParameterizedTest
//    @MethodSource("emfs")
//    fun query(emf: EntityManagerFactory) {
//
//    }
//
//    @ParameterizedTest
//    @MethodSource("emfs")
//    fun lookupByPublicKeyShortHashes(emf: EntityManagerFactory) {
//
//    }
//
//    @ParameterizedTest
//    @MethodSource("emfs")
//    fun lookupByPublicKeyHashes(emf: EntityManagerFactory) {
//
//    }
}