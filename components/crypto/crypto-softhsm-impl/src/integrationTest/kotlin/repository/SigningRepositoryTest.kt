package repository

import net.corda.cipher.suite.impl.CipherSchemeMetadataImpl
import net.corda.cipher.suite.impl.PlatformDigestServiceImpl
import net.corda.crypto.cipher.suite.GeneratedWrappedKey
import net.corda.crypto.cipher.suite.schemes.KeyScheme
import net.corda.crypto.cipher.suite.schemes.KeySchemeCapability
import net.corda.crypto.core.CryptoConsts
import net.corda.crypto.core.KEY_LOOKUP_INPUT_ITEMS_LIMIT
import net.corda.crypto.core.ShortHash
import net.corda.crypto.core.SigningKeyInfo
import net.corda.crypto.core.SigningKeyStatus
import net.corda.crypto.core.fullPublicKeyIdFromBytes
import net.corda.crypto.core.parseSecureHash
import net.corda.crypto.core.publicKeyIdFromBytes
import net.corda.crypto.persistence.SigningKeyOrderBy
import net.corda.crypto.persistence.SigningWrappedKeySaveContext
import net.corda.crypto.persistence.db.model.SigningKeyEntity
import net.corda.crypto.persistence.db.model.SigningKeyMaterialEntity
import net.corda.crypto.persistence.db.model.WrappingKeyEntity
import net.corda.crypto.softhsm.impl.SigningRepositoryImpl
import net.corda.crypto.testkit.SecureHashUtils
import net.corda.layeredpropertymap.LayeredPropertyMapFactory
import net.corda.layeredpropertymap.impl.LayeredPropertyMapImpl
import net.corda.layeredpropertymap.impl.PropertyConverter
import net.corda.orm.utils.transaction
import net.corda.orm.utils.use
import net.corda.test.util.time.toSafeWindowsPrecision
import net.corda.v5.base.types.LayeredPropertyMap
import net.corda.v5.crypto.DigestAlgorithmName
import net.corda.v5.crypto.KeySchemeCodes
import org.assertj.core.api.Assertions.assertThat
import org.bouncycastle.asn1.x509.AlgorithmIdentifier
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.MethodSource
import org.mockito.kotlin.mock
import java.security.KeyPairGenerator
import java.security.spec.AlgorithmParameterSpec
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.util.UUID
import javax.persistence.EntityManagerFactory
import javax.persistence.PersistenceException

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class SigningRepositoryTest : CryptoRepositoryTest() {
    private val cipherSchemeMetadata = CipherSchemeMetadataImpl()
    private val digestService = PlatformDigestServiceImpl(cipherSchemeMetadata)
    private val rsaScheme = cipherSchemeMetadata.findKeyScheme(KeySchemeCodes.RSA_CODE_NAME)
    private val keyPairGenerator = KeyPairGenerator.getInstance(rsaScheme.algorithmName)
    private val defaultTenantId = "Memento Mori"
    private val defaultMasterKeyName = "Domination's the name of the game"

    private fun createSigningKeyInfo(
        tenantId: String? = null,
        wrappingKeyAlias: String = defaultMasterKeyName
    ): SigningKeyInfo {
        val unique = UUID.randomUUID().toString()
        val keyPair = keyPairGenerator.generateKeyPair()
        val keyId = publicKeyIdFromBytes(keyPair.public.encoded)
        val fullKey = fullPublicKeyIdFromBytes(keyPair.public.encoded, digestService)

        return SigningKeyInfo(
            id = ShortHash.parse(keyId),
            fullId = parseSecureHash(fullKey),
            tenantId = tenantId ?: "t-$unique".take(12),
            category = "c-$unique",
            alias = "a-$unique",
            hsmAlias = null,
            publicKey = keyPair.public,
            schemeCodeName = "FOO",
            externalId = "e-$unique",
            timestamp = Instant.now().toSafeWindowsPrecision(),
            hsmId = CryptoConsts.SOFT_HSM_ID,
            status = SigningKeyStatus.NORMAL,
            keyMaterial = keyPair.private.encoded,
            encodingVersion = 1,
            wrappingKeyAlias = wrappingKeyAlias
        )
    }

    private fun createGeneratedWrappedKey(info: SigningKeyInfo): GeneratedWrappedKey {
        return GeneratedWrappedKey(info.publicKey, info.keyMaterial, info.encodingVersion!!)
    }

    private fun createKeyScheme(info: SigningKeyInfo) =
        KeyScheme(
            codeName = info.schemeCodeName,
            algorithmOIDs = listOf(mock<AlgorithmIdentifier>()),
            providerName = "foo",
            algorithmName = "bar",
            algSpec = mock<AlgorithmParameterSpec>(),
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

    data class UniqueAliasAndGeneration(
        val emf: EntityManagerFactory,
        val alias: String,
        val generation: Int
    )

    private val uniqueWrappingKeys = mutableMapOf<UniqueAliasAndGeneration, UUID>()

    /**
     * Need to ensure we only create wrapping keys for a unique combination of alias and generation to fulfil the
     * db constraint for each EMF. Tests can reuse existing wrapping keys with the same combination if they already exist.
     */
    private fun saveWrappingKey(
        emf: EntityManagerFactory,
        alias: String,
        generation: Int = 1
    ) = uniqueWrappingKeys.computeIfAbsent(UniqueAliasAndGeneration(emf, alias, generation)) {
        emf.createEntityManager().use { em ->
            em.transaction {
                it.merge(
                    WrappingKeyEntity(
                        id = UUID.randomUUID(),
                        generation = generation,
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
        }.id
    }

    private val createdKeys = mutableMapOf<EntityManagerFactory, List<SigningKeyInfo>>()

    private fun createKeys(emf: EntityManagerFactory): List<SigningKeyInfo> {
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

    fun createSigningWrappedKeySaveContext(info: SigningKeyInfo): SigningWrappedKeySaveContext {
        val privKey = createGeneratedWrappedKey(info)
        return SigningWrappedKeySaveContext(
            key = privKey,
            wrappingKeyAlias = info.wrappingKeyAlias,
            externalId = info.externalId,
            alias = info.alias,
            category = info.category,
            keyScheme = createKeyScheme(info)
        )
    }


    @ParameterizedTest
    @MethodSource("emfs")
    fun `savePrivateKey and find by alias`(emf: EntityManagerFactory) {
        val info = createSigningKeyInfo()
        saveWrappingKey(emf, info.wrappingKeyAlias)

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
    fun `savePrivateKey uses latest generation of wrapping key`(emf: EntityManagerFactory) {
        val info = createSigningKeyInfo()
        val unexpectedUuid1 = saveWrappingKey(emf, info.wrappingKeyAlias, generation = 1)
        val unexpectedUuid2 = saveWrappingKey(emf, info.wrappingKeyAlias, generation = 2)
        val expectedUuid = saveWrappingKey(emf, info.wrappingKeyAlias, generation = 3)

        assertThat(unexpectedUuid1).isNotEqualTo(expectedUuid)
        assertThat(unexpectedUuid2).isNotEqualTo(expectedUuid)

        val ctx = createSigningWrappedKeySaveContext(info)

        val repo = SigningRepositoryImpl(
            emf,
            info.tenantId,
            cipherSchemeMetadata,
            digestService,
            createLayeredPropertyMapFactory(),
        )

        repo.savePrivateKey(ctx)

        emf.createEntityManager().use { em ->
            val signingKeyResult = em.createQuery(
                "FROM ${SigningKeyEntity::class.java.simpleName} WHERE tenantId=:tenantId AND alias=:alias",
                SigningKeyEntity::class.java
            ).setParameter("tenantId", info.tenantId)
                .setParameter("alias", info.alias)
                .resultList

            assertThat(signingKeyResult.size).isEqualTo(1)

            val signingKeyMaterialResult = em.createQuery(
                "FROM ${SigningKeyMaterialEntity::class.java.simpleName} WHERE signing_key_id=:signing_key_id",
                SigningKeyMaterialEntity::class.java
            ).setParameter("signing_key_id", signingKeyResult.first().id)
                .resultList

            assertThat(signingKeyMaterialResult.size).isEqualTo(1)
            assertThat(signingKeyMaterialResult.first().wrappingKeyId).isEqualTo(expectedUuid)
        }
    }

    @ParameterizedTest
    @MethodSource("emfs")
    fun `find by alias joined the key material with the highest generation wrapping key`(emf: EntityManagerFactory) {
        val info = createSigningKeyInfo(wrappingKeyAlias = UUID.randomUUID().toString() + "alias")
        val ctx = createSigningWrappedKeySaveContext(info)
        val someOtherAlias = UUID.randomUUID().toString() + "some_other_alias"

        val repo = SigningRepositoryImpl(
            emf,
            info.tenantId,
            cipherSchemeMetadata,
            digestService,
            createLayeredPropertyMapFactory(),
        )

        // Create the first key against the generation 1 wrapping key
        saveWrappingKey(emf, info.wrappingKeyAlias, generation = 1)
        repo.savePrivateKey(ctx)

        // Do a db search to get the signing key Id
        val signingKeyResult = emf.createEntityManager().use { em ->
            em.createQuery(
                "FROM ${SigningKeyEntity::class.java.simpleName} WHERE tenantId=:tenantId AND alias=:alias",
                SigningKeyEntity::class.java
            ).setParameter("tenantId", info.tenantId)
                .setParameter("alias", info.alias)
                .resultList
        }
        assertThat(signingKeyResult.size).isEqualTo(1)

        val dummyKeyMaterialPrefix = "dummy_key_material"

        // Create 2 more wrapping keys and key materials for them for the same signing key just saved
        // This is simulating two key rotations
        repeat(2) {
            val generation = it + 2
            val wrappingKeyUuid = saveWrappingKey(emf, info.wrappingKeyAlias, generation = generation)
            val materialEntity = SigningKeyMaterialEntity(
                signingKeyId = signingKeyResult.first().id,
                wrappingKeyId = wrappingKeyUuid,
                created = Instant.now(),
                keyMaterial = "$dummyKeyMaterialPrefix$generation".toByteArray()
            )
            emf.createEntityManager().use { em ->
                em.transaction {
                    em.persist(materialEntity)
                }
            }
        }

        // Create a wrapping key with a high generation but a different alias to make sure we never pick this up
        saveWrappingKey(emf, someOtherAlias, generation = 150)

        // Check the key info found via the public api has been joined to the last of the 2 dummy key materials
        val found = checkNotNull(repo.findKey(info.alias!!))
        assertThat(found.keyMaterial.contentEquals("${dummyKeyMaterialPrefix}3".toByteArray())).isTrue()

        // Other than the keyMaterial which is deliberately changed, the signing key info should be the same after rotation
        assertThat(found)
            .usingRecursiveComparison().ignoringFields("timestamp", "keyMaterial")
            .isEqualTo(info)
    }

    @ParameterizedTest
    @MethodSource("emfs")
    fun `savePrivateKey twice fails`(emf: EntityManagerFactory) {
        val info = createSigningKeyInfo()
        saveWrappingKey(emf, info.wrappingKeyAlias)

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
        saveWrappingKey(emf, info.wrappingKeyAlias)

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
        val found = query(emf, 0, 2, SigningKeyOrderBy.ALIAS, mapOf("tenantId" to defaultTenantId))
        assertThat(found)
            .usingRecursiveFieldByFieldElementComparatorIgnoringFields("timestamp")
            .containsExactlyElementsOf(
                allKeys.filter { it.tenantId == defaultTenantId }.sortedBy { it.alias }.take(2)
            )
    }

    @ParameterizedTest
    @MethodSource("emfs")
    fun `query by tenant id - page 2`(emf: EntityManagerFactory) {
        val allKeys = createdKeys.getOrElse(emf) {
            createdKeys[emf] = createKeys(emf)
            createdKeys[emf]!!
        }
        val found = query(emf, 2, 2, SigningKeyOrderBy.ALIAS, mapOf("tenantId" to defaultTenantId))
        assertThat(found)
            .usingRecursiveFieldByFieldElementComparatorIgnoringFields("timestamp")
            .containsExactlyElementsOf(
                allKeys.filter { it.tenantId == defaultTenantId }.sortedBy { it.alias }.drop(2).take(2)
            )
    }

    @ParameterizedTest
    @MethodSource("emfs")
    fun `query by category`(emf: EntityManagerFactory) {
        val allKeys = createdKeys.getOrElse(emf) {
            createdKeys[emf] = createKeys(emf)
            createdKeys[emf]!!
        }
        val found = query(
            emf, 0, 2, SigningKeyOrderBy.CATEGORY_DESC, mapOf("category" to allKeys.first().category)
        )
        assertThat(found)
            .usingRecursiveFieldByFieldElementComparatorIgnoringFields("timestamp")
            .containsExactlyElementsOf(
                allKeys.filter { it.category == allKeys.first().category }.sortedByDescending { it.category }.take(2)
            )
    }

    @ParameterizedTest
    @MethodSource("emfs")
    fun `query by schemeCodeName`(emf: EntityManagerFactory) {
        val allKeys = createdKeys.getOrElse(emf) {
            createdKeys[emf] = createKeys(emf)
            createdKeys[emf]!!
        }
        val found = query(
            emf, 0, 2,
            SigningKeyOrderBy.ALIAS, mapOf("schemeCodeName" to allKeys.first().schemeCodeName)
        )
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
            emf, 0, 2,
            SigningKeyOrderBy.ALIAS,
            mapOf("alias" to allKeys.first { null != it.externalId }.alias!!)
        )
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
            emf, 0, 2,
            SigningKeyOrderBy.ALIAS,
            mapOf("externalId" to allKeys.first { null != it.externalId }.externalId!!)
        )
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

        val lookFor = allKeys.map { digestService.hash(it.publicKey.encoded, DigestAlgorithmName.SHA2_256) }
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
            ShortHash.of(digestService.hash(it.publicKey.encoded, DigestAlgorithmName.SHA2_256))
        }
        val found = repo.lookupByPublicKeyShortHashes(lookFor.toSet()).toList()
        assertThat(found)
            .usingRecursiveFieldByFieldElementComparatorIgnoringFields("timestamp")
            .containsExactlyInAnyOrderElementsOf(allKeys)

        // additionally going to assert that looking up by full hash should result in the same
        val lookFor2 = allKeys.map {
            parseSecureHash(fullPublicKeyIdFromBytes(it.publicKey.encoded, digestService))
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

    @ParameterizedTest
    @MethodSource("emfs")
    fun `get key materials`(emf: EntityManagerFactory) {
        val someOtherAlias = "some_other_alias"
        val tenantId = "gkm${UUID.randomUUID()}".take(12)

        val wrappingKeyUuid = saveWrappingKey(emf, alias = defaultMasterKeyName)
        saveWrappingKey(emf, alias = someOtherAlias)

        val repo = SigningRepositoryImpl(
            emf,
            tenantId,
            cipherSchemeMetadata,
            digestService,
            createLayeredPropertyMapFactory(),
        )

        // Create 3 different signing keys for the same tenant, each using the same wrapping key alias. This will result
        // in 3 key materials each wrapped by the same wrapping key.
        repeat(3) {
            val info = createSigningKeyInfo(tenantId = tenantId, wrappingKeyAlias = defaultMasterKeyName)
            val ctx = createSigningWrappedKeySaveContext(info)
            repo.savePrivateKey(ctx)
        }

        // Create 2 more signing keys using a different alias, these should not show up in our key materials we fetch from
        // the previous wrapping key uuid.
        repeat(2) {
            val info = createSigningKeyInfo(tenantId = tenantId, wrappingKeyAlias = someOtherAlias)
            val ctx = createSigningWrappedKeySaveContext(info)
            repo.savePrivateKey(ctx)
        }

        // We can now retrieve all the key materials wrapped by the defaultMasterKeyName wrapping key uid
        val keyMaterials = repo.getKeyMaterials(wrappingKeyUuid).toList()
        assertThat(keyMaterials.size).isEqualTo(3)

        assertThat(keyMaterials[0]).isNotEqualTo(keyMaterials[1])
        assertThat(keyMaterials[0]).isNotEqualTo(keyMaterials[2])
        assertThat(keyMaterials[1]).isNotEqualTo(keyMaterials[2])
    }
}
