package net.corda.crypto.client.impl.v50beta2

import net.corda.crypto.persistence.WrappingKeyInfo
import net.corda.crypto.persistence.v50beta2.V50Beta2CryptoRepositoryImpl
import net.corda.crypto.persistence.v50beta2.V50Beta2WrappingKeyEntity
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.doAnswer
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import javax.persistence.EntityManager
import kotlin.test.assertNotNull


class TestV50Beta2CryptoRepositoryImpl {
    @Test
    fun `save a wrapping key`() {
        val stored = ArrayList<V50Beta2WrappingKeyEntity>()
        val em = mock<EntityManager> {
            on { persist(any()) } doAnswer {
                stored.add(it.getArgument(0))
                Unit
            }
            on { find<V50Beta2WrappingKeyEntity>(any(), any()) } doAnswer { stored.first() }
            on { transaction } doReturn mock()
        }
        val repo = V50Beta2CryptoRepositoryImpl(mock {
            on(mock()) doReturn em
        })
        val wrappingKeyInfo = WrappingKeyInfo(1, "caesar", byteArrayOf())
        repo.saveWrappingKey("a", wrappingKeyInfo)
        val retrievedWrappingKeyInfo = repo.findWrappingKey("a")
        assertNotNull(retrievedWrappingKeyInfo)
        assertThat(wrappingKeyInfo.encodingVersion).isEqualTo(retrievedWrappingKeyInfo.encodingVersion)
    }
}