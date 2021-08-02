package net.corda.v5.base.util

import org.junit.jupiter.api.Test
import java.math.BigInteger
import java.util.*
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.test.fail

/**
 * Modified from the bitcoinj library.
 */
class Base58Test {
    @Test
    fun testEncode() {
        val testbytes = "Hello World".toByteArray()
        assertEquals("JxF12TrwUP45BMd", Base58.encode(testbytes))

        val bi = BigInteger.valueOf(3471844090L)
        assertEquals("16Ho7Hs", Base58.encode(bi.toByteArray()))

        val zeroBytes1 = ByteArray(1)
        assertEquals("1", Base58.encode(zeroBytes1))

        val zeroBytes7 = ByteArray(7)
        assertEquals("1111111", Base58.encode(zeroBytes7))

        // test empty encode
        val emptyByteArray = byteArrayOf()
        assertEquals("", Base58.encode(emptyByteArray))
    }

    @Test
    fun testDecode() {
        val testbytes = "Hello World".toByteArray()
        val actualbytes = Base58.decode("JxF12TrwUP45BMd")
        assertTrue(String(actualbytes)) { Arrays.equals(testbytes, actualbytes) }

        assertTrue("1") { Arrays.equals(ByteArray(1), Base58.decode("1")) }
        assertTrue("1111") { Arrays.equals(ByteArray(4), Base58.decode("1111")) }

        try {
            Base58.decode("This isn't valid base58")
            fail()
        } catch (e: IllegalArgumentException) {
            // expected
        }
    }

    @Test
    fun testDecodeToBigInteger() {
        val input = Base58.decode("129")
        assertEquals(BigInteger(1, input), Base58.decodeToBigInteger("129"))
    }
}
