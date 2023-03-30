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
    )

    @ParameterizedTest
    @MethodSource("emfs")
    fun saveKey(emf: EntityManagerFactory) {
        val keyAlias = "save-key-${UUID.randomUUID()}"
        val repo = WrappingRepositoryImpl(emf)

        val savedKey = repo.saveKey(keyAlias, wrappingKeyInfo)

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
        val keyAlias = "save-key-${UUID.randomUUID()}"
        val repo = WrappingRepositoryImpl(emf)

        repo.saveKey(keyAlias, wrappingKeyInfo)

        assertThrows<PersistenceException> {
            repo.saveKey(keyAlias, wrappingKeyInfo.copy(encodingVersion = 1234))
        }
    }

    @ParameterizedTest
    @MethodSource("emfs")
    fun findKey(emf: EntityManagerFactory) {
        val keyAlias = "find-key-${UUID.randomUUID()}"
        val repo = WrappingRepositoryImpl(emf)
        repo.saveKey(keyAlias, wrappingKeyInfo)

        val loadedKey = repo.findKey(keyAlias)

        assertThat(loadedKey).isEqualTo(wrappingKeyInfo)
    }

    @ParameterizedTest
    @MethodSource("emfs")
    fun `findKey return null for non existing alias`(emf: EntityManagerFactory) {
        val repo = WrappingRepositoryImpl(emf)
        val loadedKey = repo.findKey(UUID.randomUUID().toString())
        assertThat(loadedKey).isNull()
    }

}