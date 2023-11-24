package repository

import net.corda.crypto.persistence.WrappingKeyInfo
import net.corda.crypto.persistence.db.model.WrappingKeyEntity
import net.corda.crypto.softhsm.impl.WrappingRepositoryImpl
import net.corda.crypto.softhsm.impl.toDto
import net.corda.crypto.testkit.SecureHashUtils
import net.corda.orm.utils.use
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Assertions.assertInstanceOf
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
                .createQuery("FROM ${WrappingKeyEntity::class.simpleName} AS k " +
                        "WHERE k.alias = :alias", WrappingKeyEntity::class.java)
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
        val wrappingKeyInfoWithAlias = wrappingKeyInfo.copy(alias=keyAlias)
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
    fun `findKeyWithId returns the UUID and the key`(emf: EntityManagerFactory) {
        val keyAlias = "save-key-${UUID.randomUUID()}"
        val wrappingKeyInfoWithUniqueAlias = wrappingKeyInfo.copy(alias=keyAlias)
        val repo = WrappingRepositoryImpl(emf, "test")
        repo.saveKey(wrappingKeyInfoWithUniqueAlias)
        val loadedKeyAndId = repo.findKeyAndId(wrappingKeyInfoWithUniqueAlias.alias)

        assertThat(loadedKeyAndId).isNotNull()
        assertInstanceOf(UUID::class.java, loadedKeyAndId!!.first)
        assertThat(loadedKeyAndId.second).isEqualTo(wrappingKeyInfoWithUniqueAlias)
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
}
