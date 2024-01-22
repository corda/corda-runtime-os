package net.corda.crypto.softhsm.impl

import net.corda.crypto.persistence.WrappingKeyInfo
import net.corda.crypto.persistence.db.model.WrappingKeyEntity
import net.corda.crypto.testkit.SecureHashUtils
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.argThat
import org.mockito.kotlin.doAnswer
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import java.util.UUID
import javax.persistence.EntityManager


class WrappingRepositoryImplTests {

    @Test
    fun `JPA equality on primary key only rule for WrappingKeyEntities`() {
        val uuidAlpha = UUID.randomUUID()
        val uuidBeta = UUID.randomUUID()
        val golden = WrappingKeyInfo(1, "DES", byteArrayOf(), 1, "root", "k1")
        val alpha1 = makeWrappingKeyEntity(uuidAlpha, golden)
        val alpha2 = makeWrappingKeyEntity(uuidAlpha, golden.copy(algorithmName = "AES", generation = 2))
        val beta = makeWrappingKeyEntity(uuidBeta, golden)
        assertThat(alpha1).isEqualTo(alpha2)
        assertThat(alpha1).isNotEqualTo(beta)
    }

    @Test
    fun `save a wrapping key`() {
        val stored = ArrayList<WrappingKeyEntity>()
        val wrappingKeyInfo = WrappingKeyInfo(
            1, "caesar", SecureHashUtils.randomBytes(), 1, "Enoch", "alias1"
        )
        val savedWrappingKey = makeWrappingKeyEntity(UUID.randomUUID(), wrappingKeyInfo)
        val em = mock<EntityManager> {
            on { merge(any<WrappingKeyEntity>()) } doReturn (savedWrappingKey)
            on { find<WrappingKeyEntity>(any(), any()) } doAnswer { stored.first() }
            on { transaction } doReturn mock()
        }
        val repo = WrappingRepositoryImpl(
            mock {
                on { createEntityManager() } doReturn em
            },
            "test"
        )
        val savedKey = repo.saveKey(wrappingKeyInfo)
        verify(em).merge(argThat<WrappingKeyEntity> {
            this.encodingVersion == 1 &&
                this.algorithmName == "caesar" &&
                this.keyMaterial.contentEquals(wrappingKeyInfo.keyMaterial) &&
                this.generation == 1 &&
                this.parentKeyReference == "Enoch"
        })
        assertThat(savedKey).isEqualTo(wrappingKeyInfo)
    }

    @Test
    fun `save a wrapping key with id`() {
        val stored = ArrayList<WrappingKeyEntity>()
        val wrappingKeyInfo = WrappingKeyInfo(
            1, "caesar", SecureHashUtils.randomBytes(), 1, "Enoch", "alias1"
        )
        val savedWrappingKey = makeWrappingKeyEntity(UUID.randomUUID(), wrappingKeyInfo)
        val em = mock<EntityManager> {
            on { merge(any<WrappingKeyEntity>()) } doReturn (savedWrappingKey)
            on { find<WrappingKeyEntity>(any(), any()) } doAnswer { stored.first() }
            on { transaction } doReturn mock()
        }
        val repo = WrappingRepositoryImpl(
            mock {
                on { createEntityManager() } doReturn em
            },
            "test"
        )
        val id = UUID.randomUUID()
        val savedKey1 = repo.saveKeyWithId(wrappingKeyInfo, id)
        verify(em).merge(argThat<WrappingKeyEntity> {
            this.encodingVersion == 1 &&
                this.algorithmName == "caesar" &&
                this.keyMaterial.contentEquals(wrappingKeyInfo.keyMaterial) &&
                this.generation == 1 &&
                this.parentKeyReference == "Enoch" &&
                this.id == id
        })
        assertThat(savedKey1).isEqualTo(wrappingKeyInfo)

        val savedKey2 = repo.saveKeyWithId(wrappingKeyInfo, null)
        verify(em).merge(argThat<WrappingKeyEntity> {
            this.encodingVersion == 1 &&
                this.algorithmName == "caesar" &&
                this.keyMaterial.contentEquals(wrappingKeyInfo.keyMaterial) &&
                this.generation == 1 &&
                this.parentKeyReference == "Enoch" &&
                this.id != id
        })

        assertThat(savedKey2).isEqualTo(wrappingKeyInfo)
    }

    @Test
    fun `find a wrapping key and its id`() {
        val wrappingKeyInfo = WrappingKeyInfo(
            1, "caesar", SecureHashUtils.randomBytes(), 1, "Enoch", "alias1"
        )
        val newId = UUID.randomUUID()
        val savedWrappingKey = makeWrappingKeyEntity(newId, wrappingKeyInfo)
        val em = mock<EntityManager> {
            on { createQuery(any(), eq(WrappingKeyEntity::class.java)) } doAnswer {
                mock {
                    on { setParameter(any<String>(), any()) } doReturn it
                    on { setMaxResults(any()) } doReturn it
                    on { resultList } doReturn listOf(savedWrappingKey)
                }
            }
        }

        val repo = WrappingRepositoryImpl(
            mock {
                on { createEntityManager() } doReturn em
            },
            "test"
        )

        // Alias here doesn't matter, mock<EntityManager> returns savedWrappingKey regardless the alias.
        // There is an integration test dealing with the database where it checks for the alias.
        val foundKey = repo.findKeyAndId("a")

        assertThat(foundKey?.first.toString().length).isEqualTo(36)
        assertThat(foundKey?.second).isEqualTo(wrappingKeyInfo)
    }

    @Test
    fun `find a wrapping key using its alias`() {
        val wrappingKeyInfo = WrappingKeyInfo(
            1, "caesar", SecureHashUtils.randomBytes(), 1, "Enoch", "alias1"
        )
        val newId = UUID.randomUUID()
        val savedWrappingKey = makeWrappingKeyEntity(newId, wrappingKeyInfo)
        val em = mock<EntityManager> {
            on { createQuery(any(), eq(WrappingKeyEntity::class.java)) } doAnswer {
                mock {
                    on { setParameter(any<String>(), any()) } doReturn it
                    on { setMaxResults(any()) } doReturn it
                    on { resultList } doReturn listOf(savedWrappingKey)
                    on { resultStream } doReturn listOf(savedWrappingKey).stream()

                }
            }
        }

        val repo = WrappingRepositoryImpl(
            mock {
                on { createEntityManager() } doReturn em
            },
            "test"
        )

        // Alias here doesn't matter, mock<EntityManager> returns savedWrappingKey regardless the alias.
        // There is an integration test dealing with the database where it checks for the alias.
        val keys = repo.findKeysWrappedByParentKey("alias1").toList()

        assertThat(keys).hasSize(1)
        assertThat(keys.first()).isEqualTo(wrappingKeyInfo)
    }

    @Test
    fun `find a wrapping key using its UUID`() {
        val wrappingKeyInfo = WrappingKeyInfo(
            1, "caesar", SecureHashUtils.randomBytes(), 1, "Enoch", "alias1"
        )
        val newId = UUID.randomUUID()
        val savedWrappingKey = makeWrappingKeyEntity(newId, wrappingKeyInfo)
        val em = mock<EntityManager> {
            on { createQuery(any(), eq(WrappingKeyEntity::class.java)) } doAnswer {
                mock {
                    on { setParameter(any<String>(), any()) } doReturn it
                    on { setMaxResults(any()) } doReturn it
                    on { resultStream } doReturn listOf(savedWrappingKey).stream()

                }
            }
        }

        val repo = WrappingRepositoryImpl(
            mock {
                on { createEntityManager() } doReturn em
            },
            "test"
        )

        // ID here doesn't matter, mock<EntityManager> returns savedWrappingKey regardless the id.
        // There is an integration test dealing with the database where it checks for the id.
        val foundKey = repo.getKeyById(newId)

        assertThat(foundKey).isEqualTo(wrappingKeyInfo)
    }


    private fun makeWrappingKeyEntity(
        newId: UUID,
        wrappingKeyInfo: WrappingKeyInfo,
    ): WrappingKeyEntity = WrappingKeyEntity(
        newId,
        "alias1",
        wrappingKeyInfo.generation,
        mock(),
        wrappingKeyInfo.encodingVersion,
        wrappingKeyInfo.algorithmName,
        wrappingKeyInfo.keyMaterial,
        mock(),
        false,
        wrappingKeyInfo.parentKeyAlias
    )
}
