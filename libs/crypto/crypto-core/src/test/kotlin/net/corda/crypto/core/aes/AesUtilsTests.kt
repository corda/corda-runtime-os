package net.corda.crypto.core.aes

import net.corda.crypto.core.ManagedSecret
import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Test
import java.time.Instant
import kotlin.random.Random
import kotlin.test.assertEquals
import kotlin.test.assertFalse

class AesUtilsTests {
    @Test
    fun `Should generate same HMAC(SHA256) for the same data`() {
        val random = Random(Instant.now().toEpochMilli())
        val data = random.nextBytes(random.nextInt(1, 193))
        val secret = ManagedSecret.generate()
        val hmac1 = secret.hmacOf(data)
        val hmac2 = secret.hmacOf(data)
        assertEquals(32, hmac1.size)
        assertEquals(32, hmac2.size)
        assertArrayEquals(hmac1, hmac2)
    }

    @Test
    fun `Should generate different HMAC(SHA256) for different data`() {
        val random = Random(Instant.now().toEpochMilli())
        val data1 = random.nextBytes(193)
        val data2 = random.nextBytes(193)
        val secret = ManagedSecret.generate()
        val hmac1 = secret.hmacOf(data1)
        val hmac2 = secret.hmacOf(data2)
        assertEquals(32, hmac1.size)
        assertEquals(32, hmac2.size)
        assertFalse(hmac1.contentEquals(hmac2))
    }

    @Test
    fun `Should generate different HMAC(SHA256) for the different secrets but same data`() {
        val random = Random(Instant.now().toEpochMilli())
        val data = random.nextBytes(random.nextInt(1, 193))
        val secret1 = ManagedSecret.generate()
        val secret2 = ManagedSecret.generate()
        val hmac1 = secret1.hmacOf(data)
        val hmac2 = secret2.hmacOf(data)
        assertEquals(32, hmac1.size)
        assertEquals(32, hmac2.size)
        assertFalse(hmac1.contentEquals(hmac2))
    }
}