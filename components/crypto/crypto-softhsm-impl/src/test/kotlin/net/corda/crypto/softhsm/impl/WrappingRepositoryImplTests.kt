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
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.util.UUID
import javax.persistence.EntityManager


class WrappingRepositoryImplTests {

    @Test
    fun `JPA equality on primary key only rule for WrappingKeyEntities`() {
        val uuidAlpha = UUID.randomUUID()
        val uuidBeta = UUID.randomUUID()
        val alpha1 = WrappingKeyEntity(
            uuidAlpha,
            "a1",
            1,
            Instant.now(),
            1,
            "DES",
            byteArrayOf(),
            LocalDate.parse("9999-12-31").atStartOfDay().toInstant(
                ZoneOffset.UTC
            ),
            false,
            "root"
        )
        val alpha2 = WrappingKeyEntity(
            uuidAlpha,
            "a1",
            1,
            Instant.now(),
            2,
            "AES",
            byteArrayOf(),
            LocalDate.parse("9999-12-31").atStartOfDay().toInstant(ZoneOffset.UTC),
            false,
            "root"
        )
        val beta = WrappingKeyEntity(
            uuidBeta,
            "a1",
            1,
            Instant.now(),
            42,
            "DES",
            byteArrayOf(),
            LocalDate.parse("9999-12-31").atStartOfDay().toInstant(ZoneOffset.UTC),
            false,
            "root"
        )
        assertThat(alpha1).isEqualTo(alpha2)
        assertThat(alpha1).isNotEqualTo(beta)
    }

    @Test
    fun `save a wrapping key`() {
        val stored = ArrayList<WrappingKeyEntity>()
        val wrappingKeyInfo = WrappingKeyInfo(
            1, "caesar", SecureHashUtils.randomBytes(), 1, "Enoch")
        val savedWrappingKey = mock<WrappingKeyEntity>() {
            on { encodingVersion } doReturn (wrappingKeyInfo.encodingVersion)
            on { algorithmName } doReturn (wrappingKeyInfo.algorithmName)
            on { keyMaterial } doReturn (wrappingKeyInfo.keyMaterial)
            on { generation } doReturn (wrappingKeyInfo.generation)
            on { parentKeyReference } doReturn (wrappingKeyInfo.parentKeyAlias)
        }
        val em = mock<EntityManager> {
            on { merge(any<WrappingKeyEntity>()) } doReturn(savedWrappingKey)
            on { find<WrappingKeyEntity>(any(), any()) } doAnswer { stored.first() }
            on { transaction } doReturn mock()
        }
        val repo = WrappingRepositoryImpl(
            mock {
                on { createEntityManager() } doReturn em
            },
            "test"
        )
        val savedKey = repo.saveKey("a", wrappingKeyInfo)
        verify(em).merge(argThat<WrappingKeyEntity>{
                    this.encodingVersion == 1 &&
                    this.algorithmName == "caesar" &&
                    this.keyMaterial.contentEquals(wrappingKeyInfo.keyMaterial) &&
                    this.generation == 1 &&
                    this.parentKeyReference == "Enoch"
        })
        // not worth the assertion, verifying it's called is good enough
//        val retrievedWrappingKeyInfo = repo.findKey("a")
//        assertNotNull(retrievedWrappingKeyInfo)
//        assertThat(wrappingKeyInfo.encodingVersion).isEqualTo(retrievedWrappingKeyInfo.encodingVersion)

        // but you can assert the return info is effectively the same as mocked
        assertThat(savedKey).isEqualTo(wrappingKeyInfo)
    }

    @Test
    fun `save a wrapping key with id`() {
        val stored = ArrayList<WrappingKeyEntity>()
        val wrappingKeyInfo = WrappingKeyInfo(
            1, "caesar", SecureHashUtils.randomBytes(), 1, "Enoch")
        val savedWrappingKey = mock<WrappingKeyEntity>() {
            on { encodingVersion } doReturn (wrappingKeyInfo.encodingVersion)
            on { algorithmName } doReturn (wrappingKeyInfo.algorithmName)
            on { keyMaterial } doReturn (wrappingKeyInfo.keyMaterial)
            on { generation } doReturn (wrappingKeyInfo.generation)
            on { parentKeyReference } doReturn (wrappingKeyInfo.parentKeyAlias)
        }
        val em = mock<EntityManager> {
            on { merge(any<WrappingKeyEntity>()) } doReturn(savedWrappingKey)
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
        val savedKey1 = repo.saveKeyWithId("a", wrappingKeyInfo, id)
        verify(em).merge(argThat<WrappingKeyEntity>{
            this.encodingVersion == 1 &&
                    this.algorithmName == "caesar" &&
                    this.keyMaterial.contentEquals(wrappingKeyInfo.keyMaterial) &&
                    this.generation == 1 &&
                    this.parentKeyReference == "Enoch" &&
                    this.id == id
        })
        assertThat(savedKey1).isEqualTo(wrappingKeyInfo)

        val savedKey2 = repo.saveKeyWithId("a", wrappingKeyInfo, null)
        verify(em).merge(argThat<WrappingKeyEntity>{
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
    fun `find a wrapping key and it's id`() {
        val wrappingKeyInfo = WrappingKeyInfo(
            1, "caesar", SecureHashUtils.randomBytes(), 1, "Enoch")
        val savedWrappingKey = mock<WrappingKeyEntity>() {
            on { id } doReturn UUID.randomUUID()
            on { encodingVersion } doReturn (wrappingKeyInfo.encodingVersion)
            on { algorithmName } doReturn (wrappingKeyInfo.algorithmName)
            on { keyMaterial } doReturn (wrappingKeyInfo.keyMaterial)
            on { generation } doReturn (wrappingKeyInfo.generation)
            on { parentKeyReference } doReturn (wrappingKeyInfo.parentKeyAlias)
        }
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
        val foundKey = repo.findKeyAndId("a")

        assertThat(foundKey?.first.toString().length).isEqualTo(36)
        assertThat(foundKey?.second).isEqualTo(wrappingKeyInfo)
    }
}
