package net.corda.crypto.impl.serializer

import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout
import org.mockito.Mockito.mock
import java.security.PrivateKey

class PrivateKeySerializationTests {
    @Test
    @Timeout(5)
    fun `Should throw IllegalStateException when serializing a private key`() {
        val privateKey = mock(PrivateKey::class.java)
        assertThatThrownBy { PrivateKeySerializer().toProxy(privateKey) }
            .isInstanceOf(IllegalStateException::class.java)
    }

    @Test
    @Timeout(5)
    fun `Should throw IllegalStateException when deserializing a private key`() {
        assertThatThrownBy { PrivateKeySerializer().fromProxy("mock") }
            .isInstanceOf(IllegalStateException::class.java)
    }
}