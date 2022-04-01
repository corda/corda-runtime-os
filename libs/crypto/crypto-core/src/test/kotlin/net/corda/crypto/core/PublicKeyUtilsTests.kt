package net.corda.crypto.core

import org.junit.jupiter.api.Test
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import java.security.PublicKey
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals

class PublicKeyUtilsTests {
    @Test
    fun `Should produce the public key id with length of 12 for a byte array`() {
        val byteArray = UUID.randomUUID().toString().toByteArray()
        val id = publicKeyIdOf(byteArray)
        assertEquals(12, id.length)
    }

    @Test
    fun `Should produce the same public key id for equal byte arrays`() {
        val byteArray = UUID.randomUUID().toString().toByteArray()
        val id1 = publicKeyIdOf(byteArray)
        val id2 = publicKeyIdOf(byteArray)
        assertEquals(id1, id2)
    }

    @Test
    fun `Should produce the different public key id for different byte arrays`() {
        val ids = (0 until 100).map {
            publicKeyIdOf(UUID.randomUUID().toString().toByteArray())
        }
        for (i in ids.indices) {
            ids.filterIndexed { index, _ -> index != i }.forEach {
                assertNotEquals(ids[i], it)
            }
        }
    }

    @Test
    fun `Should produce the public key id with length of 12 for a public key`() {
        val bytes = UUID.randomUUID().toString().toByteArray()
        val publicKey = mock<PublicKey> { on { encoded } doReturn bytes  }
        val id = publicKeyIdOf(publicKey)
        assertEquals(12, id.length)
    }

    @Test
    fun `Should produce the same public key id for same public key`() {
        val bytes = UUID.randomUUID().toString().toByteArray()
        val publicKey = mock<PublicKey> { on { encoded } doReturn bytes  }
        val id1 = publicKeyIdOf(publicKey)
        val id2 = publicKeyIdOf(publicKey)
        assertEquals(id1, id2)
    }

    @Test
    fun `Should produce the different public key id for different public keys`() {
        val ids = (0 until 100).map {
            val bytes = UUID.randomUUID().toString().toByteArray()
            val publicKey = mock<PublicKey> { on { encoded } doReturn bytes  }
            publicKeyIdOf(publicKey)
        }
        for (i in ids.indices) {
            ids.filterIndexed { index, _ -> index != i }.forEach {
                assertNotEquals(ids[i], it)
            }
        }
    }
}