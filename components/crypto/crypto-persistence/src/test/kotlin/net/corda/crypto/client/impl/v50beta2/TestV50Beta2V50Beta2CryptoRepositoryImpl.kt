package net.corda.crypto.client.impl.v50beta2

import net.corda.crypto.persistence.WrappingKeyInfo
import net.corda.crypto.persistence.v50beta2.V50Beta2CryptoRepositoryImpl
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.doAnswer
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import javax.persistence.EntityManager


class TestV50Beta2CryptoRepositoryImpl {
    @Test
    fun `save a wrapping key`() {
        val em: EntityManager = mock {
            on { persist(any()) } doAnswer {}
            on { transaction } doReturn mock()
        }
        em.persist(1)
        val repo = V50Beta2CryptoRepositoryImpl(mock {
            on(mock()) doReturn em
        })
        val wrappingKeyInfo = WrappingKeyInfo(1, "caesar", byteArrayOf())
        repo.saveWrappingKey("a", wrappingKeyInfo)
        val retrievedWrappingKeyInfo = repo.findWrappingKey("a")
        assertThat(wrappingKeyInfo).isEqualTo(retrievedWrappingKeyInfo)
    }
    
}