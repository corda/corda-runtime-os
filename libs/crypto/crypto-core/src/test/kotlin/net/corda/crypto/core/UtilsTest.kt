package net.corda.crypto.core

import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.nio.ByteBuffer
import kotlin.test.assertContentEquals

class UtilsTest {
    @Test
    fun `Test concat function`() {
        val bytes1 = ByteArray(0)
        val bytes2 = "Test".toByteArray()
        val bytes3 = "Things".toByteArray()
        val concat1 = concatByteArrays(bytes1, bytes2)
        assertArrayEquals(bytes2, concat1)
        val concat2 = concatByteArrays(bytes2)
        assertArrayEquals(bytes2, concat2)
        val concat3 = concatByteArrays(bytes2, bytes2)
        assertEquals("TestTest", concat3.toString(Charsets.UTF_8))
        val concat4 = concatByteArrays(bytes2, bytes1, bytes3, bytes2, bytes1)
        assertEquals("TestThingsTest", concat4.toString(Charsets.UTF_8))
    }

    @Test
    fun `Should convert Int to ByteArray`() {
        val result = 73.toByteArray()
        assertContentEquals(byteArrayOf(0.toByte(), 0.toByte(), 0.toByte(), 73.toByte()), result)
        val zero = 0.toByteArray()
        assertContentEquals(byteArrayOf(0.toByte(), 0.toByte(), 0.toByte(), 0.toByte()), zero)
        val min = Int.MIN_VALUE.toByteArray()
        assertContentEquals(byteArrayOf(0x80.toByte(), 0.toByte(), 0.toByte(), 0.toByte()), min)
        val max = Int.MAX_VALUE.toByteArray()
        assertContentEquals(byteArrayOf(0x7F.toByte(), 0xFF.toByte(), 0xFF.toByte(), 0xFF.toByte()), max)
    }

    @Test
    fun `Should convert Long to ByteArray`() {
        val result = 73L.toByteArray()
        assertContentEquals(
            byteArrayOf(
                0.toByte(),
                0.toByte(),
                0.toByte(),
                0.toByte(),
                0.toByte(),
                0.toByte(),
                0.toByte(),
                73.toByte()
            ), result
        )
        val zero = 0L.toByteArray()
        assertContentEquals(
            byteArrayOf(
                0.toByte(),
                0.toByte(),
                0.toByte(),
                0.toByte(),
                0.toByte(),
                0.toByte(),
                0.toByte(),
                0.toByte()
            ), zero
        )
        val min = Long.MIN_VALUE.toByteArray()
        assertContentEquals(
            byteArrayOf(
                0x80.toByte(),
                0.toByte(),
                0.toByte(),
                0.toByte(),
                0.toByte(),
                0.toByte(),
                0.toByte(),
                0.toByte()
            ), min
        )
        val max = Long.MAX_VALUE.toByteArray()
        assertContentEquals(
            byteArrayOf(
                0x7F.toByte(),
                0xFF.toByte(),
                0xFF.toByte(),
                0xFF.toByte(),
                0xFF.toByte(),
                0xFF.toByte(),
                0xFF.toByte(),
                0xFF.toByte()
            ), max
        )
    }

    fun intToBytes(i: Int): ByteArray =
        ByteBuffer.allocate(Int.SIZE_BYTES).putInt(i).array()

    fun bytesToInt(bytes: ByteArray): Int =
        ByteBuffer.wrap(bytes).int
}