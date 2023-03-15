package net.corda.crypto.core

import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

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
}