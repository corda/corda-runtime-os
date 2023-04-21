package net.corda.v5.base.util

import net.corda.v5.base.util.EncodingUtils.base58ToRealString
import net.corda.v5.base.util.EncodingUtils.base58toBase64
import net.corda.v5.base.util.EncodingUtils.base58toHex
import net.corda.v5.base.util.EncodingUtils.base64ToRealString
import net.corda.v5.base.util.EncodingUtils.base64toBase58
import net.corda.v5.base.util.EncodingUtils.base64toHex
import net.corda.v5.base.util.EncodingUtils.hexToBase58
import net.corda.v5.base.util.EncodingUtils.hexToBase64
import net.corda.v5.base.util.EncodingUtils.hexToRealString
import net.corda.v5.base.util.EncodingUtils.toBase58
import net.corda.v5.base.util.EncodingUtils.toBase64
import net.corda.v5.base.util.EncodingUtils.toHex
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.fail

class EncodingUtilsTest {

    val testString = "Hello World"
    val testBytes = testString.toByteArray()
    val testBase58String = "JxF12TrwUP45BMd" // Base58 format for Hello World.
    val testBase64String = "SGVsbG8gV29ybGQ=" // Base64 format for Hello World.
    val testHexString = "48656C6C6F20576F726C64" // HEX format for Hello World.

    // Encoding tests
    @Test
    fun `encoding Hello World`() {
        assertEquals(testBase58String, toBase58(testBytes))
        assertEquals(testBase64String, toBase64(testBytes))
        assertEquals(testHexString, toHex(testBytes))
    }

    @Test
    fun `empty encoding`() {
        val emptyByteArray = byteArrayOf()
        assertEquals("", toBase58(emptyByteArray))
        assertEquals("", toBase64(emptyByteArray))
        assertEquals("", toHex(emptyByteArray))
    }

    @Test
    fun `encoding 7 zero bytes`() {
        val sevenZeroByteArray = ByteArray(7)
        assertEquals("1111111", toBase58(sevenZeroByteArray))
        assertEquals("AAAAAAAAAA==", toBase64(sevenZeroByteArray))
        assertEquals("00000000000000", toHex(sevenZeroByteArray))
    }

    //Decoding tests
    @Test
    fun `decoding to real String`() {
        assertEquals(testString, base58ToRealString(testBase58String))
        assertEquals(testString, base64ToRealString(testBase64String))
        assertEquals(testString, hexToRealString(testHexString))
    }

    @Test
    fun `decoding empty Strings`() {
        assertEquals("", base58ToRealString(""))
        assertEquals("", base64ToRealString(""))
        assertEquals("", hexToRealString(""))
    }

    @Test
    fun `decoding lowercase and mixed HEX`() {
        val testHexStringLowercase = testHexString.lowercase()
        assertEquals(hexToRealString(testHexString), hexToRealString(testHexStringLowercase))

        val testHexStringMixed = testHexString.replace('C', 'c')
        assertEquals(hexToRealString(testHexString), hexToRealString(testHexStringMixed))
    }

    @Test
    fun `decoding on wrong format`() {
        // the String "Hello World" is not a valid Base58 or Base64 or HEX format
        try {
            base58ToRealString(testString)
            fail()
        } catch (e: IllegalArgumentException) {
            // expected.
        }

        try {
            base64ToRealString(testString)
            fail()
        } catch (e: IllegalArgumentException) {
            // expected.
        }

        try {
            hexToRealString(testString)
            fail()
        } catch (e: IllegalArgumentException) {
            // expected.
        }
    }

    //Encoding changers tests
    @Test
    fun `change encoding between base58, base64, hex`() {
        // base58 to base64
        assertEquals(testBase64String, base58toBase64(testBase58String))
        // base58 to hex
        assertEquals(testHexString, base58toHex(testBase58String))
        // base64 to base58
        assertEquals(testBase58String, base64toBase58(testBase64String))
        // base64 to hex
        assertEquals(testHexString, base64toHex(testBase64String))
        // hex to base58
        assertEquals(testBase58String, hexToBase58(testHexString))
        // hex to base64
        assertEquals(testBase64String, hexToBase64(testHexString))
    }
}
