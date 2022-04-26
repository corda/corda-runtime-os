package net.corda.crypto.impl.components

import net.corda.v5.crypto.DigestAlgorithmName
import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Test
import java.io.ByteArrayInputStream
import java.security.MessageDigest
import kotlin.random.Random

class DoubleSHA256DigestTests {
    @Test
    fun `Should calculate hash for array`() {
        val service = DoubleSHA256Digest()
        val random = Random(17)
        for ( i in 1..100) {
            val len = random.nextInt(127, 277)
            val data = ByteArray(len)
            random.nextBytes(data)
            val actual = service.digest(data)
            val expected = MessageDigest.getInstance(DigestAlgorithmName.SHA2_256.name).digest(
                MessageDigest.getInstance(DigestAlgorithmName.SHA2_256.name).digest(data)
            )
            assertArrayEquals(expected, actual)
        }
    }

    @Test
    fun `Should calculate hash for short input streams`() {
        val service = DoubleSHA256Digest()
        val random = Random(17)
        for ( i in 1..100) {
            val len = random.nextInt(1, 100)
            val data = ByteArray(len)
            random.nextBytes(data)
            val stream = ByteArrayInputStream(data)
            val actual = service.digest(stream)
            val expected = MessageDigest.getInstance(DigestAlgorithmName.SHA2_256.name).digest(
                MessageDigest.getInstance(DigestAlgorithmName.SHA2_256.name).digest(data)
            )
            assertArrayEquals(expected, actual)
        }
    }

    @Test
    fun `Should calculate hash for medium sized input streams`() {
        val service = DoubleSHA256Digest()
        val random = Random(17)
        for ( i in 1..100) {
            val len = random.nextInt(375, 2074)
            val data = ByteArray(len)
            random.nextBytes(data)
            val stream = ByteArrayInputStream(data)
            val actual = service.digest(stream)
            val expected = MessageDigest.getInstance(DigestAlgorithmName.SHA2_256.name).digest(
                MessageDigest.getInstance(DigestAlgorithmName.SHA2_256.name).digest(data)
            )
            assertArrayEquals(expected, actual)
        }
    }

    @Test
    fun `Should calculate hash for large sized input streams`() {
        val service = DoubleSHA256Digest()
        val random = Random(17)
        for ( i in 1..10) {
            val len = random.nextInt(37_794, 63_987)
            val data = ByteArray(len)
            random.nextBytes(data)
            val stream = ByteArrayInputStream(data)
            val actual = service.digest(stream)
            val expected = MessageDigest.getInstance(DigestAlgorithmName.SHA2_256.name).digest(
                MessageDigest.getInstance(DigestAlgorithmName.SHA2_256.name).digest(data)
            )
            assertArrayEquals(expected, actual)
        }
    }

    @Test
    fun `Should calculate hash for input streams with sizes around buffer size`() {
        val service = DoubleSHA256Digest()
        val random = Random(17)
        for ( len in (DoubleSHA256Digest.STREAM_BUFFER_SIZE - 5)..((DoubleSHA256Digest.STREAM_BUFFER_SIZE + 5))) {
            val data = ByteArray(len)
            random.nextBytes(data)
            val stream = ByteArrayInputStream(data)
            val actual = service.digest(stream)
            val expected = MessageDigest.getInstance(DigestAlgorithmName.SHA2_256.name).digest(
                MessageDigest.getInstance(DigestAlgorithmName.SHA2_256.name).digest(data)
            )
            assertArrayEquals(expected, actual)
        }
    }
}