package net.corda.crypto.cipher.suite

import net.corda.crypto.cipher.suite.mocks.generateKeyPair
import net.corda.crypto.cipher.suite.mocks.specs
import net.corda.v5.base.types.ByteArrays.toHexString
import net.corda.v5.crypto.DigestAlgorithmName
import net.corda.v5.crypto.sha256Bytes
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.MethodSource
import java.security.MessageDigest
import java.security.PublicKey
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class PublicKeyHashTests {
    companion object {
        @JvmStatic
        fun publicKeys(): List<PublicKey> = specs.values.map {
            generateKeyPair(it).public
        }
    }

    @Test
    fun `hash is computed correctly for a given byte array`() {
        val bytes = "abc".toByteArray().sha256Bytes()
        val hash = PublicKeyHash.parse(bytes)
        assertEquals(
            "BA7816BF8F01CFEA414140DE5DAE2223B00361A396177A9CB410FF61F20015AD",
            hash.value
        )
    }

    @Test
    fun `hash is computed correctly for a given string`() {
        val str = "BA7816BF8F01CFEA414140DE5DAE2223B00361A396177A9CB410FF61F20015AD"
        val hash = PublicKeyHash.parse(str)
        assertEquals(str, hash.value)
    }

    @ParameterizedTest
    @MethodSource("publicKeys")
    fun `hash is computed correctly for a given public key using top level function`(publicKey: PublicKey) {
        val hash = publicKey.calculateHash()
        val expected = toHexString(
            MessageDigest.getInstance(DigestAlgorithmName.SHA2_256.name).digest(publicKey.encoded))
        assertEquals(expected, hash.value)
    }

    @ParameterizedTest
    @MethodSource("publicKeys")
    fun `hash is computed correctly for a given public key`(publicKey: PublicKey) {
        val hash = PublicKeyHash.calculate(publicKey)
        val expected = toHexString(
            MessageDigest.getInstance(DigestAlgorithmName.SHA2_256.name).digest(publicKey.encoded))
        assertEquals(expected, hash.value)
    }

    @ParameterizedTest
    @MethodSource("publicKeys")
    fun `hash is computed correctly for a given encoded public key`(publicKey: PublicKey) {
        val hash = PublicKeyHash.calculate(publicKey.encoded)
        val expected = toHexString(
            MessageDigest.getInstance(DigestAlgorithmName.SHA2_256.name).digest(publicKey.encoded))
        assertEquals(expected, hash.value)
    }

    @ParameterizedTest
    @MethodSource("publicKeys")
    fun `id is computed correctly for a given public key`(publicKey: PublicKey) {
        val hash = PublicKeyHash.calculate(publicKey)
        val expected = toHexString(
            MessageDigest.getInstance(DigestAlgorithmName.SHA2_256.name).digest(publicKey.encoded))
        assertEquals(expected.substring(0, 12), hash.id)
    }

    @ParameterizedTest
    @MethodSource("publicKeys")
    fun `id is computed correctly for a given public key using top level function`(publicKey: PublicKey) {
        val id = publicKey.publicKeyId()
        val expected = toHexString(
            MessageDigest.getInstance(DigestAlgorithmName.SHA2_256.name).digest(publicKey.encoded))
        assertEquals(expected.substring(0, 12), id)
    }

    @Test
    fun `toString output is Hex representation of hash value`() {
        val bytes = "abc".toByteArray().sha256Bytes()
        val hash = PublicKeyHash.parse(bytes)
        assertEquals(
            "BA7816BF8F01CFEA414140DE5DAE2223B00361A396177A9CB410FF61F20015AD",
            hash.toString()
        )
    }

    @Test
    fun `throws IllegalArgumentException if input byte length is not 32`() {
        val bytes = "abc".toByteArray()
        assertFailsWith(IllegalArgumentException::class) {
            PublicKeyHash.parse(bytes)
        }
    }

    @Test
    fun `throws IllegalArgumentException if input string length is not 64`() {
        val str = "abc"
        assertFailsWith(IllegalArgumentException::class) {
            PublicKeyHash.parse(str)
        }
    }

    @Test
    fun `throws IllegalArgumentException if input is an invalid hex string`() {
        val str = "ZZ7816BF8F01CFEA414140DE5DAE2223B00361A396177A9CB410FF61F20015AD"
        assertFailsWith(IllegalArgumentException::class) {
            PublicKeyHash.parse(str)
        }
    }

    @Test
    fun `correctly returns false while comparing with null value`() {
        val bytes = "abc".toByteArray().sha256Bytes()
        val hash = PublicKeyHash.parse(bytes)
        assertFalse(hash.equals(null))
    }

    @Test
    fun `correctly compares hash value with string input`() {
        val bytes = "abc".toByteArray().sha256Bytes()
        val hash = PublicKeyHash.parse(bytes)
        assertTrue(hash.equals("BA7816BF8F01CFEA414140DE5DAE2223B00361A396177A9CB410FF61F20015AD"))
        assertFalse(hash.equals("INCORRECTSTRING"))
    }

    @Test
    fun `correctly compares hash value with byte array input`() {
        val bytes = "abc".toByteArray().sha256Bytes()
        val hash = PublicKeyHash.parse(bytes)
        assertTrue(hash.equals(bytes))
        assertFalse(hash.equals("def".toByteArray()))
    }

    @Test
    fun `correctly compares hash objects based on hash value`() {
        val bytes = "abc".toByteArray().sha256Bytes()
        val hash1 = PublicKeyHash.parse(bytes)
        val hash2 = PublicKeyHash.parse(bytes)
        val hash3 = PublicKeyHash.parse("def".toByteArray().sha256Bytes())
        assertEquals(hash1, hash2)
        assertNotEquals(hash1, hash3)
    }

    @Test
    fun `correctly returns false while comparing hash object with invalid input type`() {
        val bytes = "abc".toByteArray().sha256Bytes()
        val hash = PublicKeyHash.parse(bytes)
        assertFalse(hash.equals(5))
    }
}