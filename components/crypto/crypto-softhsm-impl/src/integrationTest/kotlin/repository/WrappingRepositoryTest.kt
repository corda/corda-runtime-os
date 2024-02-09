package repository

import net.corda.crypto.core.CryptoConsts
import net.corda.crypto.persistence.WrappingKeyInfo
import net.corda.crypto.persistence.db.model.SigningKeyEntity
import net.corda.crypto.persistence.db.model.SigningKeyEntityStatus
import net.corda.crypto.persistence.db.model.SigningKeyMaterialEntity
import net.corda.crypto.persistence.db.model.WrappingKeyEntity
import net.corda.crypto.softhsm.impl.WrappingRepositoryImpl
import net.corda.crypto.softhsm.impl.toDto
import net.corda.crypto.testkit.SecureHashUtils
import net.corda.orm.utils.transaction
import net.corda.orm.utils.use
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.MethodSource
import java.time.Instant
import java.util.*
import javax.persistence.EntityManagerFactory
import javax.persistence.PersistenceException

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class WrappingRepositoryTest : CryptoRepositoryTest() {
    private val wrappingKeyInfo = WrappingKeyInfo(
        encodingVersion = 123,
        algorithmName = "algo-123",
        keyMaterial = SecureHashUtils.randomBytes(),
        generation = 666,
        parentKeyAlias = "Ned Flanders",
        alias = "alias"
    )

    @ParameterizedTest
    @MethodSource("emfs")
    fun saveKey(emf: EntityManagerFactory) {
        val keyAlias = "save-key-${UUID.randomUUID()}"
        val repo = WrappingRepositoryImpl(emf, "test")

        val savedKey = repo.saveKey(wrappingKeyInfo.copy(alias = keyAlias))

        val loadedKey = emf.createEntityManager().use {
            it
                .createQuery(
                    "FROM ${WrappingKeyEntity::class.simpleName} AS k " +
                            "WHERE k.alias = :alias", WrappingKeyEntity::class.java
                )
                .setParameter("alias", keyAlias)
                .singleResult

        }.toDto()

        assertThat(loadedKey).isEqualTo(savedKey)
    }

    @ParameterizedTest
    @MethodSource("emfs")
    fun `saveKey duplicate alias should throw`(emf: EntityManagerFactory) {
        val repo = WrappingRepositoryImpl(emf, "test")

        repo.saveKey(wrappingKeyInfo)

        assertThrows<PersistenceException> {
            repo.saveKey(wrappingKeyInfo.copy(encodingVersion = 1234))
        }
    }

    @ParameterizedTest
    @MethodSource("emfs")
    fun findKey(emf: EntityManagerFactory) {
        val keyAlias = "find-key-${UUID.randomUUID()}"
        val wrappingKeyInfoWithAlias = wrappingKeyInfo.copy(alias = keyAlias)
        val repo = WrappingRepositoryImpl(emf, "test")
        repo.saveKey(wrappingKeyInfoWithAlias)

        val loadedKey = repo.findKey(keyAlias)

        assertThat(loadedKey).isEqualTo(wrappingKeyInfoWithAlias)
    }

    @ParameterizedTest
    @MethodSource("emfs")
    fun `findKey return null for non existing alias`(emf: EntityManagerFactory) {
        val repo = WrappingRepositoryImpl(emf, "test")
        val loadedKey = repo.findKey(UUID.randomUUID().toString())
        assertThat(loadedKey).isNull()
    }

    @ParameterizedTest
    @MethodSource("emfs")
    fun `findKeyAndId returns the UUID and the key`(emf: EntityManagerFactory) {
        val keyAlias = "save-key-${UUID.randomUUID()}"
        val wrappingKeyInfoWithUniqueAlias = wrappingKeyInfo.copy(alias = keyAlias)
        val repo = WrappingRepositoryImpl(emf, "test")
        repo.saveKey(wrappingKeyInfoWithUniqueAlias)
        val loadedKeyAndId = repo.findKeyAndId(wrappingKeyInfoWithUniqueAlias.alias)

        assertThat(loadedKeyAndId).isNotNull()
        assertThat(loadedKeyAndId!!.second).isEqualTo(wrappingKeyInfoWithUniqueAlias)
    }

    @ParameterizedTest
    @MethodSource("emfs")
    fun `findKeyAndId returns latest generation key`(emf: EntityManagerFactory) {
        val keyAlias = "save-key-${UUID.randomUUID()}"
        val wrappingKeyInfoWithUniqueAlias = wrappingKeyInfo.copy(alias = keyAlias)
        val repo = WrappingRepositoryImpl(emf, "test")
        repo.saveKey(wrappingKeyInfoWithUniqueAlias)

        val wrappingKeyGeneration2 =
            wrappingKeyInfo.copy(generation = wrappingKeyInfoWithUniqueAlias.generation + 1, alias = keyAlias)
        repo.saveKey(wrappingKeyGeneration2)
        val wrappingKeyGeneration3 =
            wrappingKeyInfo.copy(generation = wrappingKeyGeneration2.generation + 1, alias = keyAlias)
        repo.saveKey(wrappingKeyGeneration3)

        val loadedKeyAndId = repo.findKeyAndId(keyAlias)

        assertThat(loadedKeyAndId).isNotNull()
        assertThat(loadedKeyAndId!!.second).isEqualTo(wrappingKeyGeneration3)
    }

    @ParameterizedTest
    @MethodSource("emfs")
    fun `saveKeyWithId with existing UUID updates the record`(emf: EntityManagerFactory) {
        val keyAlias = "find-key-${UUID.randomUUID()}"
        val wrappingKeyInfoWithUniqueAlias = WrappingKeyInfo(
            encodingVersion = 234,
            algorithmName = "algo-234",
            keyMaterial = SecureHashUtils.randomBytes(),
            generation = 777,
            parentKeyAlias = "Salamander",
            alias = keyAlias
        )

        val repo = WrappingRepositoryImpl(emf, "test")
        repo.saveKey(wrappingKeyInfoWithUniqueAlias)
        val loadedKeyAndId = repo.findKeyAndId(wrappingKeyInfoWithUniqueAlias.alias)
        val updatedKey = repo.saveKeyWithId(wrappingKeyInfoWithUniqueAlias, loadedKeyAndId?.first)
        val loadedKeyAndId2 = repo.findKeyAndId(wrappingKeyInfoWithUniqueAlias.alias)

        assertThat(updatedKey).isEqualTo(wrappingKeyInfoWithUniqueAlias)
        assertThat(loadedKeyAndId).isNotNull()
        assertThat(loadedKeyAndId2).isNotNull()
        assertThat(loadedKeyAndId!!.first).isEqualTo(loadedKeyAndId2!!.first)
    }

    @ParameterizedTest
    @MethodSource("emfs")
    fun `getKeyByID returns null for non existing id`(emf: EntityManagerFactory) {
        val repo = WrappingRepositoryImpl(emf, "test")
        val loadedKey = repo.getKeyById(UUID.randomUUID())
        assertThat(loadedKey).isNull()
    }

    @ParameterizedTest
    @MethodSource("emfs")
    fun `findKeysNotWrappedByParentKey returns latest generation key`(emf: EntityManagerFactory) {
        val repo = WrappingRepositoryImpl(emf, "test")

        // Pairs are parent key alias, number of generations of wrapping key to create
        listOf(
            Pair("pka1", 1),
            Pair("pka1", 3),
            Pair("pka2", 2),
            Pair("pka3", 1),
            Pair("pka3", 2),
            Pair("pka3", 3)
        ).forEach {
            val keyAlias = "save-key-${UUID.randomUUID()}"
            var wrappingKeyInfo = wrappingKeyInfo.copy(alias = keyAlias, parentKeyAlias = it.first)

            repeat(it.second) {
                repo.saveKey(wrappingKeyInfo)
                wrappingKeyInfo = wrappingKeyInfo.copy(generation = wrappingKeyInfo.generation + 1, alias = keyAlias)
            }
        }

        // We have no control over what order the results come back in, so we make sure the correct generation wrapping
        // keys are returned in any order where required

        val resultsPka1 = repo.findKeysNotWrappedByParentKey("pka1")

        val resultPka1filtered1 = resultsPka1.filter { it.parentKeyAlias == "pka2" }
        assertThat(resultPka1filtered1.size).isEqualTo(1)
        assertThat(resultPka1filtered1[0].generation).isEqualTo(wrappingKeyInfo.generation + 1)

        val resultPka1filtered2 = resultsPka1.filter { it.parentKeyAlias == "pka3" }
        assertThat(resultPka1filtered2.size).isEqualTo(3)
        val generationSetPka1 = mutableSetOf<Int>()
        resultPka1filtered2.forEach {
            generationSetPka1 += it.generation
        }
        assertThat(generationSetPka1).contains(wrappingKeyInfo.generation)
        assertThat(generationSetPka1).contains(wrappingKeyInfo.generation + 1)
        assertThat(generationSetPka1).contains(wrappingKeyInfo.generation + 2)

        val resultsPka2 = repo.findKeysNotWrappedByParentKey("pka2")
        val resultPka2filtered1 = resultsPka2.filter { it.parentKeyAlias == "pka1" }
        assertThat(resultPka2filtered1.size).isEqualTo(2)
        val generationSetPka2 = mutableSetOf<Int>()
        resultPka1filtered2.forEach {
            generationSetPka2 += it.generation
        }
        assertThat(generationSetPka2).contains(wrappingKeyInfo.generation)
        assertThat(generationSetPka2).contains(wrappingKeyInfo.generation + 1)
    }

    @ParameterizedTest
    @MethodSource("emfs")
    fun `getAllKeyIds returns latest generation keys for the tenant`(emf: EntityManagerFactory) {
        val dummyKeyMaterialPrefix = "dummy_key_material"
        val tenantIdUnderTest = "test_tenant"
        val repo = WrappingRepositoryImpl(emf, tenantIdUnderTest)

        val expectedWrappingKeys = mutableSetOf<Pair<UUID, String>>()

        // Pair is number of generations of wrapping key/key material to create for a signing key, tenant Id
        // Create at least one signing key/key material/wrapping key for a different tenant to simulate the situation in
        // the cluster crypto wrapping repo (this wrapping key uuid should not be returned by getAllKeyIds).
        listOf(
            Pair(1, tenantIdUnderTest),
            Pair(2, tenantIdUnderTest),
            Pair(3, tenantIdUnderTest),
            Pair(1, "other_tenant")
        ).forEach {
            val wrappingKeyAlias = "wrapping-key-${UUID.randomUUID()}"
            val signingKeyAlias = "signing-key-${UUID.randomUUID()}"
            var wrappingKeyInfo = wrappingKeyInfo.copy(alias = wrappingKeyAlias)
            val signingKeyId = UUID.randomUUID()
            var lastWrappingKeyUuid = UUID.randomUUID()

            // Save the new signing key
            val entity = SigningKeyEntity(
                id = signingKeyId,
                tenantId = it.second,
                keyId = signingKeyId.toString().take(12),
                fullKeyId = "123456789012",
                created = Instant.now(),
                category = "category",
                schemeCodeName = "schemeCodeName",
                publicKey = "publicKeyBytes".toByteArray(),
                encodingVersion = 1,
                alias = signingKeyAlias,
                hsmAlias = null,
                externalId = "externalId",
                hsmId = CryptoConsts.SOFT_HSM_ID,
                status = SigningKeyEntityStatus.NORMAL
            )
            emf.createEntityManager().use {
                it.transaction {
                    it.persist(entity)
                }
            }

            repeat(it.first) {
                // Save the new wrapping key (using the repo)
                repo.saveKey(wrappingKeyInfo)

                lastWrappingKeyUuid = repo.findKeyAndId(wrappingKeyAlias)!!.let {
                    // As we're using the repo under test to grab the UUID, best check it's returning the one we expected
                    assertThat(it.second.generation).isEqualTo(wrappingKeyInfo.generation)
                    it.first
                }

                // Update the wrapping key info for next time round
                wrappingKeyInfo =
                    wrappingKeyInfo.copy(generation = wrappingKeyInfo.generation + 1, alias = wrappingKeyAlias)

                // Save the key material which points to this generation of wrapping key and signing key
                val materialEntity = SigningKeyMaterialEntity(
                    signingKeyId = signingKeyId,
                    wrappingKeyId = lastWrappingKeyUuid,
                    created = Instant.now(),
                    keyMaterial = "$dummyKeyMaterialPrefix$it".toByteArray()
                )
                emf.createEntityManager().use { em ->
                    em.transaction {
                        em.persist(materialEntity)
                    }
                }
            }

            // Store the last wrapping key uuid generated into the expected set, assuming it's for the correct tenant
            if (tenantIdUnderTest == it.second) {
                expectedWrappingKeys.add(Pair(lastWrappingKeyUuid, wrappingKeyAlias))
            }
        }

        val results = repo.getAllKeyIdsAndAliases()
        assertThat(results).isEqualTo(expectedWrappingKeys)
    }
}
