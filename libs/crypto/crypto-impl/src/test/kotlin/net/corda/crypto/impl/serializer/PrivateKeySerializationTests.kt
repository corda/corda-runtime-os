package net.corda.crypto.impl.serializer

import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout
import org.mockito.Mockito.mock
import java.security.PrivateKey

@Timeout(30)
class PrivateKeySerializationTests {

    @Test
    fun `check throws when serializing a private key`() {
        val privateKey = mock(PrivateKey::class.java)
        assertThatThrownBy { PrivateKeySerializer().toProxy(privateKey) }
            .isInstanceOf(IllegalStateException::class.java)
            .hasMessageContaining("Attempt to serialise private key")
    }
}