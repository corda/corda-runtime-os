package repository

import net.corda.cipher.suite.impl.CipherSchemeMetadataImpl
import net.corda.cipher.suite.impl.PlatformDigestServiceImpl
import net.corda.crypto.cipher.suite.GeneratedPublicKey
import net.corda.crypto.cipher.suite.GeneratedWrappedKey
import net.corda.crypto.cipher.suite.schemes.KeyScheme
import net.corda.crypto.cipher.suite.schemes.KeySchemeCapability
import net.corda.crypto.core.KEY_LOOKUP_INPUT_ITEMS_LIMIT
import net.corda.crypto.core.ShortHash
import net.corda.crypto.core.fullPublicKeyIdFromBytes
import net.corda.crypto.core.parseSecureHash
import net.corda.crypto.core.publicKeyIdFromBytes
import net.corda.crypto.persistence.SigningKeyInfo
import net.corda.crypto.persistence.SigningKeyOrderBy
import net.corda.crypto.persistence.SigningKeyStatus
import net.corda.crypto.persistence.SigningWrappedKeySaveContext
import net.corda.crypto.persistence.WrappingKeyInfo
import net.corda.crypto.persistence.db.model.WrappingKeyEntity
import net.corda.crypto.softhsm.impl.SigningRepositoryImpl
import net.corda.crypto.softhsm.impl.toDto
import net.corda.crypto.testkit.SecureHashUtils
import net.corda.layeredpropertymap.LayeredPropertyMapFactory
import net.corda.layeredpropertymap.impl.LayeredPropertyMapImpl
import net.corda.layeredpropertymap.impl.PropertyConverter
import net.corda.orm.utils.transaction
import net.corda.orm.utils.use
import net.corda.test.util.time.toSafeWindowsPrecision
import net.corda.v5.base.types.LayeredPropertyMap
import org.assertj.core.api.Assertions.assertThat
import org.bouncycastle.asn1.x509.AlgorithmIdentifier
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.MethodSource
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import java.lang.IllegalArgumentException
import java.security.PublicKey
import java.security.spec.AlgorithmParameterSpec
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.util.*
import javax.persistence.EntityManagerFactory
import javax.persistence.PersistenceException

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class SigningRepositoryTest : CryptoRepositoryTest() {
    private val cipherSchemeMetadata = CipherSchemeMetadataImpl()
    private val digestService = PlatformDigestServiceImpl(cipherSchemeMetadata)

    private val defaultTenantId = "Memento Mori"
    private val defaultMasterKeyName = "Domination's the name of the game"

    private fun createSigningKeyInfo(): SigningKeyInfo {
        val privKey = SecureHashUtils.randomBytes()
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
            hsmAlias = null,
            publicKey = key,
            schemeCodeName = "FOO",
            externalId = "e-$unique",
            timestamp = Instant.now().toSafeWindowsPrecision(),
            hsmId = "hi-$unique".take(36),
            status = SigningKeyStatus.NORMAL,
            keyMaterial = privKey,
            encodingVersion = 1,
            masterKeyAlias = defaultMasterKeyName
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
        return GeneratedWrappedKey(pubKey, info.keyMaterial, info.encodingVersion!!)
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

    private fun createLayeredPropertyMapFactory(): LayeredPropertyMapFactory {
        return object : LayeredPropertyMapFactory {
            override fun createMap(properties: Map<String, String?>): LayeredPropertyMap {
                return LayeredPropertyMapImpl(properties, PropertyConverter(emptyMap()))
            }

        }
    }

    private val wrappingKeys = mutableMapOf<EntityManagerFactory, WrappingKeyInfo>()
    private fun saveWrappingKey(emf: EntityManagerFactory, alias: String): WrappingKeyInfo {
        return wrappingKeys.computeIfAbsent(emf) {
            emf.createEntityManager().use { em ->
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
    }

    private val createdKeys = mutableMapOf<EntityManagerFactory, List<SigningKeyInfo>>()

    private fun createKeys(emf: EntityManagerFactory) : List<SigningKeyInfo> {
        val repo = SigningRepositoryImpl(
            emf,
            defaultTenantId,
            cipherSchemeMetadata,
            digestService,
            createLayeredPropertyMapFactory(),
        )

        saveWrappingKey(emf, defaultMasterKeyName)
        val privateKeys = (0..2).map { _ ->
            val info = createSigningKeyInfo()
            val ctx = createSigningWrappedKeySaveContext(info)
            repo.savePrivateKey(ctx)
        }


        return privateKeys
    }

    fun createSigningWrappedKeySaveContext(info: SigningKeyInfo) : SigningWrappedKeySaveContext {
        val privKey = createGeneratedWrappedKey(info)
        return SigningWrappedKeySaveContext(
            key = privKey,
            masterKeyAlias = info.masterKeyAlias,
            externalId = info.externalId,
            alias = info.alias,
            category = info.category,
            keyScheme = createKeyScheme(info),
            hsmId = info.hsmId,
        )
    }


    @ParameterizedTest
    @MethodSource("emfs")
    fun `savePrivateKey and find by alias`(emf: EntityManagerFactory) {
        val info = createSigningKeyInfo()
        saveWrappingKey(emf, info.masterKeyAlias!!)

        val ctx = createSigningWrappedKeySaveContext(info)

        val repo = SigningRepositoryImpl(
            emf,
            info.tenantId,
            cipherSchemeMetadata,
            digestService,
            createLayeredPropertyMapFactory(),
        )

        repo.savePrivateKey(ctx)

        val found = repo.findKey(info.alias!!)

        assertThat(found)
            .usingRecursiveComparison().ignoringFields("timestamp")
            .isEqualTo(info)
    }

    @ParameterizedTest
    @MethodSource("emfs")
    fun `savePrivateKey twice fails`(emf: EntityManagerFactory) {
        val info = createSigningKeyInfo()
        saveWrappingKey(emf, info.masterKeyAlias!!)

        val ctx = createSigningWrappedKeySaveContext(info)

        val repo = SigningRepositoryImpl(
            emf,
            info.tenantId,
            cipherSchemeMetadata,
            digestService,
            createLayeredPropertyMapFactory(),
        )

        repo.savePrivateKey(ctx)
        assertThrows<PersistenceException> {
            repo.savePrivateKey(ctx)
        }
    }

    @ParameterizedTest
    @MethodSource("emfs")
    fun `save same key for 2 tenants should work`(emf: EntityManagerFactory) {
        val info = createSigningKeyInfo()
        saveWrappingKey(emf, info.masterKeyAlias!!)

        val ctx = createSigningWrappedKeySaveContext(info)

        // save first
        val repo = SigningRepositoryImpl(
            emf,
            info.tenantId,
            cipherSchemeMetadata,
            digestService,
            createLayeredPropertyMapFactory(),
        )
        repo.savePrivateKey(ctx)

        // clone
        val secondKeyInfo = info.copy(tenantId = "${info.tenantId.take(4)}-buddy")

        // should also work
        val repo2 = SigningRepositoryImpl(
            emf,
            secondKeyInfo.tenantId,
            cipherSchemeMetadata,
            digestService,
            createLayeredPropertyMapFactory(),
        )
        repo2.savePrivateKey(createSigningWrappedKeySaveContext(secondKeyInfo))
    }

    @ParameterizedTest
    @MethodSource("emfs")
    fun `query by tenant id`(emf: EntityManagerFactory) {
        val allKeys = createdKeys.getOrElse(emf) {
            createdKeys[emf] = createKeys(emf)
            createdKeys[emf]!!
        }
        val found = query(emf,0, 2, SigningKeyOrderBy.ALIAS, mapOf("tenantId" to defaultTenantId))
        assertThat(found)
            .usingRecursiveFieldByFieldElementComparatorIgnoringFields("timestamp")
            .containsExactlyElementsOf(
                allKeys.filter { it.tenantId == defaultTenantId }.sortedBy { it.alias }.take(2))
    }

    @ParameterizedTest
    @MethodSource("emfs")
    fun `query by tenant id - page 2`(emf: EntityManagerFactory) {
        val allKeys = createdKeys.getOrElse(emf) {
            createdKeys[emf] = createKeys(emf)
            createdKeys[emf]!!
        }
        val found = query(emf,2, 2, SigningKeyOrderBy.ALIAS, mapOf("tenantId" to defaultTenantId))
        assertThat(found)
            .usingRecursiveFieldByFieldElementComparatorIgnoringFields("timestamp")
            .containsExactlyElementsOf(
                allKeys.filter { it.tenantId == defaultTenantId }.sortedBy { it.alias }.drop(2).take(2))
    }

    @ParameterizedTest
    @MethodSource("emfs")
    fun `query by category`(emf: EntityManagerFactory) {
        val allKeys = createdKeys.getOrElse(emf) {
            createdKeys[emf] = createKeys(emf)
            createdKeys[emf]!!
        }
        val found = query(
            emf,0, 2, SigningKeyOrderBy.CATEGORY_DESC, mapOf("category" to allKeys.first().category))
        assertThat(found)
            .usingRecursiveFieldByFieldElementComparatorIgnoringFields("timestamp")
            .containsExactlyElementsOf(
                allKeys.filter { it.category == allKeys.first().category }.sortedByDescending { it.category }.take(2))
    }

    @ParameterizedTest
    @MethodSource("emfs")
    fun `query by schemeCodeName`(emf: EntityManagerFactory) {
        val allKeys = createdKeys.getOrElse(emf) {
            createdKeys[emf] = createKeys(emf)
            createdKeys[emf]!!
        }
        val found = query(
            emf,0, 2,
            SigningKeyOrderBy.ALIAS, mapOf("schemeCodeName" to allKeys.first().schemeCodeName))
        assertThat(found)
            .usingRecursiveFieldByFieldElementComparatorIgnoringFields("timestamp")
            .containsExactlyElementsOf(
                allKeys
                    .filter { it.schemeCodeName == allKeys.first().schemeCodeName }
                    .sortedBy { it.alias }
                    .take(2))
    }

    @ParameterizedTest
    @MethodSource("emfs")
    fun `query by alias`(emf: EntityManagerFactory) {
        val allKeys = createdKeys.getOrElse(emf) {
            createdKeys[emf] = createKeys(emf)
            createdKeys[emf]!!
        }
        val found = query(
            emf,0, 2,
            SigningKeyOrderBy.ALIAS,
            mapOf("alias" to allKeys.first{ null != it.externalId }.alias!!))
        assertThat(found)
            .usingRecursiveFieldByFieldElementComparatorIgnoringFields("timestamp")
            .containsExactlyElementsOf(
                allKeys
                    .filter { it.alias == allKeys.first().alias }
                    .sortedBy { it.alias }
                    .take(2))
    }

    @ParameterizedTest
    @MethodSource("emfs")
    fun `query by externalId`(emf: EntityManagerFactory) {
        val allKeys = createdKeys.getOrElse(emf) {
            createdKeys[emf] = createKeys(emf)
            createdKeys[emf]!!
        }
        val found = query(
            emf,0, 2,
            SigningKeyOrderBy.ALIAS,
            mapOf("externalId" to allKeys.first { null != it.externalId }.externalId!!))
        assertThat(found)
            .usingRecursiveFieldByFieldElementComparatorIgnoringFields("timestamp")
            .containsExactlyElementsOf(
                allKeys
                    .filter { it.externalId == allKeys.first().externalId }
                    .sortedBy { it.externalId }
                    .take(2))
    }

    private fun query(
        emf: EntityManagerFactory,
        skip: Int,
        take: Int,
        orderBy: SigningKeyOrderBy,
        filters: Map<String, String>,
    ): List<SigningKeyInfo> {
        val repo = SigningRepositoryImpl(
            emf,
            defaultTenantId,
            cipherSchemeMetadata,
            digestService,
            createLayeredPropertyMapFactory(),
        )

        return repo.query(skip, take, orderBy, filters).toList()
    }


    @ParameterizedTest
    @MethodSource("emfs")
    fun lookupByPublicKeyShortHashes(emf: EntityManagerFactory) {
        val allKeys = createdKeys.getOrElse(emf) {
            createdKeys[emf] = createKeys(emf)
            createdKeys[emf]!!
        }

        val repo = SigningRepositoryImpl(
            emf,
            defaultTenantId,
            cipherSchemeMetadata,
            digestService,
            createLayeredPropertyMapFactory(),
        )

        val lookFor = allKeys.map {
            parseSecureHash(fullPublicKeyIdFromBytes(it.publicKey, digestService))
        }
        val found = repo.lookupByPublicKeyHashes(lookFor.toSet()).toList()

        assertThat(found)
            .usingRecursiveFieldByFieldElementComparatorIgnoringFields("timestamp")
            .containsExactlyInAnyOrderElementsOf(allKeys)
    }

    @ParameterizedTest
    @MethodSource("emfs")
    fun `lookupByPublicKeyShortHashes returns empty when none found`(emf: EntityManagerFactory) {
        val repo = SigningRepositoryImpl(
            emf,
            defaultTenantId,
            cipherSchemeMetadata,
            digestService,
            createLayeredPropertyMapFactory(),
        )

        val found = repo.lookupByPublicKeyHashes(setOf(SecureHashUtils.randomSecureHash()))
        assertThat(found).isEmpty()
    }

    @ParameterizedTest
    @MethodSource("emfs")
    fun `lookupByPublicKeyShortHashes throws when asking too many`(emf: EntityManagerFactory) {
        val repo = SigningRepositoryImpl(
            emf,
            defaultTenantId,
            cipherSchemeMetadata,
            digestService,
            createLayeredPropertyMapFactory(),
        )

        assertThrows<IllegalArgumentException> {
            val keys = (0..KEY_LOOKUP_INPUT_ITEMS_LIMIT).map { SecureHashUtils.randomSecureHash() }
            repo.lookupByPublicKeyHashes(keys.toSet())
        }
    }

    @ParameterizedTest
    @MethodSource("emfs")
    fun lookupByPublicKeyHashes(emf: EntityManagerFactory) {
        val allKeys = createdKeys.getOrElse(emf) {
            createdKeys[emf] = createKeys(emf)
            createdKeys[emf]!!
        }

        val repo = SigningRepositoryImpl(
            emf,
            defaultTenantId,
            cipherSchemeMetadata,
            digestService,
            createLayeredPropertyMapFactory(),
        )

        val lookFor = allKeys.map {
            ShortHash.of(parseSecureHash(fullPublicKeyIdFromBytes(it.publicKey, digestService)))
        }
        val found = repo.lookupByPublicKeyShortHashes(lookFor.toSet()).toList()
        assertThat(found)
            .usingRecursiveFieldByFieldElementComparatorIgnoringFields("timestamp")
            .containsExactlyInAnyOrderElementsOf(allKeys)

        // additionally going to assert that looking up by full hash should result in the same
        val lookFor2 = allKeys.map {
            parseSecureHash(fullPublicKeyIdFromBytes(it.publicKey, digestService))
        }
        val found2 = repo.lookupByPublicKeyHashes(lookFor2.toSet())
        assertThat(found2).containsExactlyInAnyOrderElementsOf(found)
    }

    @ParameterizedTest
    @MethodSource("emfs")
    fun `lookupByPublicKeyHashes returns empty if none found`(emf: EntityManagerFactory) {
        val repo = SigningRepositoryImpl(
            emf,
            defaultTenantId,
            cipherSchemeMetadata,
            digestService,
            createLayeredPropertyMapFactory(),
        )

        val found = repo.lookupByPublicKeyShortHashes(setOf(ShortHash.of(SecureHashUtils.randomSecureHash())))
        assertThat(found).isEmpty()
    }

    @ParameterizedTest
    @MethodSource("emfs")
    fun `lookupByPublicKeyHashes throws when asking too many`(emf: EntityManagerFactory) {
        val repo = SigningRepositoryImpl(
            emf,
            defaultTenantId,
            cipherSchemeMetadata,
            digestService,
            createLayeredPropertyMapFactory(),
        )

        assertThrows<IllegalArgumentException> {
            val keys = (0..KEY_LOOKUP_INPUT_ITEMS_LIMIT).map { ShortHash.of(SecureHashUtils.randomSecureHash()) }
            repo.lookupByPublicKeyShortHashes(keys.toSet())
        }
    }
}
