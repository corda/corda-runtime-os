package net.corda.p2p.linkmanager

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import java.security.PublicKey

class KeyHasherTest {

    @Test
    fun `toHash returns SHA-256 hash`() {
        val key = mock<PublicKey> {
            on { encoded } doReturn byteArrayOf(1, 2, 3, 4)
        }
        val hasher = KeyHasher()

        assertThat(hasher.hash(key)).containsExactly(
            -97, 100, -89, 71, -31, -71, 127, 19, 31, -85, -74,
            -76, 71, 41, 108, -101, 111, 2, 1, -25, -97, -77,
            -59, 53, 110, 108, 119, -24, -101, 106, -128, 106,
        )
    }
}
