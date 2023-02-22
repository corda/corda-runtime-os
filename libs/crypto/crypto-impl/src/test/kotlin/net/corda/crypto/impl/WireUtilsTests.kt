package net.corda.crypto.impl

import net.corda.crypto.cipher.suite.AlgorithmParameterSpecEncodingService
import net.corda.crypto.cipher.suite.CustomSignatureSpec
import net.corda.crypto.cipher.suite.schemes.SerializedAlgorithmParameterSpec
import net.corda.crypto.core.sha256Bytes
import net.corda.data.KeyValuePair
import net.corda.data.KeyValuePairList
import net.corda.data.crypto.wire.CryptoSignatureParameterSpec
import net.corda.data.crypto.wire.CryptoSignatureSpec
import net.corda.v5.base.util.EncodingUtils.toHex
import net.corda.v5.crypto.DigestAlgorithmName
import net.corda.v5.crypto.ParameterizedSignatureSpec
import net.corda.v5.crypto.SignatureSpec
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Test
import org.mockito.Mockito
import org.mockito.kotlin.any
import org.mockito.kotlin.doAnswer
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import java.nio.ByteBuffer
import java.security.spec.AlgorithmParameterSpec
import java.time.Instant
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class WireUtilsTests {
    @Test
    fun `Should transform non empty wire context list to map`() {
        val list = listOf(
            KeyValuePair("key1", "value11"),
            KeyValuePair("key2", "value2")
        )
        val map = list.toMap()
        assertEquals(2, map.size)
        assertTrue(map.any { it.key == "key1" && it.value == "value11" })
        assertTrue(map.any { it.key == "key2" && it.value == "value2" })
    }

    @Test
    fun `Should transform non empty wire context to map`() {
        val list = KeyValuePairList(listOf(
            KeyValuePair("key1", "value11"),
            KeyValuePair("key2", "value2")
        ))
        val map = list.toMap()
        assertEquals(2, map.size)
        assertTrue(map.any { it.key == "key1" && it.value == "value11" })
        assertTrue(map.any { it.key == "key2" && it.value == "value2" })
    }

    @Test
    fun `Should transform empty wire context list to map`() {
        val list = emptyList<KeyValuePair>()
        val map = list.toMap()
        assertTrue(map.isEmpty())
    }

    @Test
    fun `Should transform empty map to wire context`() {
        val map = emptyMap<String, String>()
        val list = map.toWire()
        assertNotNull(list.items)
        assertTrue(list.items.isEmpty())
    }

    @Test
    fun `Should transform non empty map to wire context`() {
        val map = mapOf(
            "key1" to "value11",
            "key2" to "value2"
        )
        val list = map.toWire()
        assertThat(list.items).hasSize(2)
        assertTrue(list.items.any { it.key == "key1" && it.value == "value11" })
        assertTrue(list.items.any { it.key == "key2" && it.value == "value2" })
    }

    @Test
    fun `Should create wire request context for a given caller`() {
        val tenantId = toHex(UUID.randomUUID().toString().toByteArray().sha256Bytes()).take(12)
        val other = KeyValuePairList(
            listOf(
                KeyValuePair("key1", "value1")
            )
        )
        val ctx = createWireRequestContext<WireUtilsTests>(UUID.randomUUID().toString(), tenantId, other)
        assertEquals(WireUtilsTests::class.simpleName, ctx.requestingComponent)
        assertThat(ctx.requestTimestamp)
            .isAfterOrEqualTo(Instant.now().minusSeconds(30))
            .isBeforeOrEqualTo(Instant.now())
        assertNotNull(ctx.requestId)
        assertEquals(tenantId, ctx.tenantId)
        assertThat(ctx.other.items).hasSize(1)
        assertTrue(ctx.other.items.any { it.key == "key1" && it.value == "value1" })
    }

    @Test
    fun `Should convert CryptoSignatureSpec to CustomSignatureSpec`() {
        var className = ""
        var bytes = ByteArray(0)
        val algSpec = mock<AlgorithmParameterSpec>()
        val serializer = mock<AlgorithmParameterSpecEncodingService> {
            on { deserialize(any()) } doAnswer {
                val p = it.getArgument(0) as SerializedAlgorithmParameterSpec
                className = p.clazz
                bytes = p.bytes
                algSpec
            }
        }
        val paramBytes = UUID.randomUUID().toString().toByteArray()
        val origin = CryptoSignatureSpec(
            "name1",
            "custom2",
            CryptoSignatureParameterSpec("class1", ByteBuffer.wrap(paramBytes))
        )
        val result = origin.toSignatureSpec(serializer)
        assertEquals(CustomSignatureSpec::class.java, result::class.java)
        result as CustomSignatureSpec
        assertEquals("name1", result.signatureName)
        assertEquals("custom2", result.customDigestName.name)
        assertNotNull(result.params)
        assertEquals("class1", className)
        assertArrayEquals(paramBytes, bytes)
    }

    @Test
    fun `Should convert CryptoSignatureSpec to ParameterizedSignatureSpec`() {
        var className = ""
        var bytes = ByteArray(0)
        val algSpec = mock<AlgorithmParameterSpec>()
        val serializer = mock<AlgorithmParameterSpecEncodingService> {
            on { deserialize(any()) } doAnswer {
                val p = it.getArgument(0) as SerializedAlgorithmParameterSpec
                className = p.clazz
                bytes = p.bytes
                algSpec
            }
        }
        val paramBytes = UUID.randomUUID().toString().toByteArray()
        val origin = CryptoSignatureSpec(
            "name1",
            null,
            CryptoSignatureParameterSpec("class1", ByteBuffer.wrap(paramBytes))
        )
        val result = origin.toSignatureSpec(serializer)
        assertEquals(ParameterizedSignatureSpec::class.java, result::class.java)
        result as ParameterizedSignatureSpec
        assertEquals("name1", result.signatureName)
        assertNotNull(result.params)
        assertEquals("class1", className)
        assertArrayEquals(paramBytes, bytes)
    }

    @Test
    fun `Should convert CryptoSignatureSpec to SignatureSpec`() {
        val serializer = mock<AlgorithmParameterSpecEncodingService>()
        val origin = CryptoSignatureSpec(
            "name1",
            null,
            null
        )
        val result = origin.toSignatureSpec(serializer)
        assertEquals(SignatureSpec::class.java, result::class.java)
        assertEquals("name1", result.signatureName)
        Mockito.verify(serializer, never()).deserialize(any())
    }

    @Test
    fun `Should convert SignatureSpec to wire with spec params and custom digest`() {
        val paramBytes = UUID.randomUUID().toString().toByteArray()
        val algSpec = mock<AlgorithmParameterSpec>()
        val serializer = mock<AlgorithmParameterSpecEncodingService> {
            on { serialize(any()) } doReturn SerializedAlgorithmParameterSpec("class1", paramBytes)
        }
        val origin = CustomSignatureSpec(
            signatureName = "name1",
            params = algSpec,
            customDigestName = DigestAlgorithmName.SHA2_256,
        )
        val result = origin.toWire(serializer)
        assertEquals("name1", result.signatureName)
        assertNotNull(result.customDigestName)
        assertEquals("SHA-256", result.customDigestName)
        assertNotNull(result.params)
        assertEquals("class1", result.params.className)
        assertArrayEquals(paramBytes, result.params.bytes.array())
    }

    @Test
    fun `Should convert SignatureSpec to wire with spec params`() {
        val paramBytes = UUID.randomUUID().toString().toByteArray()
        val algSpec = mock<AlgorithmParameterSpec>()
        val serializer = mock<AlgorithmParameterSpecEncodingService> {
            on { serialize(any()) } doReturn SerializedAlgorithmParameterSpec("class1", paramBytes)
        }
        val origin = ParameterizedSignatureSpec(
            signatureName = "name1",
            params = algSpec
        )
        val result = origin.toWire(serializer)
        assertEquals("name1", result.signatureName)
        assertNull(result.customDigestName)
        assertNotNull(result.params)
        assertEquals("class1", result.params.className)
        assertArrayEquals(paramBytes, result.params.bytes.array())
    }

    @Test
    fun `Should convert SignatureSpec to wire without spec params and without custom digest`() {
        val serializer = mock<AlgorithmParameterSpecEncodingService>()
        val origin = SignatureSpec("name1")
        val result = origin.toWire(serializer)
        assertEquals("name1", result.signatureName)
        assertNull(result.customDigestName)
        assertNull(result.customDigestName)
        assertNull(result.params)
        Mockito.verify(serializer, never()).serialize(any())
    }
}