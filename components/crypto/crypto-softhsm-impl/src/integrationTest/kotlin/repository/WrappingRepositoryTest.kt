package repository

import net.corda.crypto.persistence.WrappingKeyInfo
import net.corda.crypto.persistence.db.model.WrappingKeyEntity
import net.corda.crypto.softhsm.impl.WrappingRepositoryImpl
import net.corda.crypto.softhsm.impl.toDto
import net.corda.crypto.testkit.SecureHashUtils
import net.corda.orm.utils.use
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.MethodSource
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
    fun `findKeysWrappedByParentKey returns latest generation key`(emf: EntityManagerFactory) {
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

        val resultsPka1 = repo.findKeysWrappedByParentKey("pka1")
        assertThat(resultsPka1.size).isEqualTo(2)
        val generationSetPka1 = mutableSetOf<Int>()
        resultsPka1.forEach {
            assertThat(it.parentKeyAlias).isEqualTo("pka1")
            generationSetPka1 += it.generation
        }
        assertThat(generationSetPka1).contains(wrappingKeyInfo.generation)
        assertThat(generationSetPka1).contains(wrappingKeyInfo.generation + 2)

        val resultsPka2 = repo.findKeysWrappedByParentKey("pka2")
        assertThat(resultsPka2.size).isEqualTo(1)
        assertThat(resultsPka2[0].parentKeyAlias).isEqualTo("pka2")
        assertThat(resultsPka2[0].generation).isEqualTo(wrappingKeyInfo.generation + 1)

        val resultsPka3 = repo.findKeysWrappedByParentKey("pka3")
        assertThat(resultsPka3.size).isEqualTo(3)
        val generationSetPka3 = mutableSetOf<Int>()
        resultsPka3.forEach {
            assertThat(it.parentKeyAlias).isEqualTo("pka3")
            generationSetPka3 += it.generation
        }
        assertThat(generationSetPka3).contains(wrappingKeyInfo.generation)
        assertThat(generationSetPka3).contains(wrappingKeyInfo.generation + 1)
        assertThat(generationSetPka3).contains(wrappingKeyInfo.generation + 2)
    }
}
