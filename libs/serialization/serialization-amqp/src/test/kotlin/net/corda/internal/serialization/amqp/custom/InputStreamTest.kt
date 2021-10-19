package net.corda.internal.serialization.amqp.custom

import net.corda.internal.serialization.amqp.EnumEvolveTests
import net.corda.internal.serialization.amqp.EvolvabilityTests
import net.corda.internal.serialization.amqp.ReusableSerialiseDeserializeAssert.Companion.serializeDeserialize
import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Test
import java.io.ByteArrayInputStream
import java.io.InputStream
import java.net.URL
import java.nio.charset.Charset
import kotlin.test.assertNotNull

class InputStreamTest {
    @Test
    fun empty() {
        ByteArrayInputStream.nullInputStream().use {
            val deserializedInputStream = serializeDeserialize(it)
            assertArrayEquals(byteArrayOf(), deserializedInputStream.readAllBytes())
        }
    }

    @Test
    fun byteStreamWithContent() {
        val byteArray = "ABC".toByteArray(Charset.defaultCharset())

        ByteArrayInputStream(byteArray).use {
            val deserializedInputStream = serializeDeserialize<InputStream>(it)
            assertArrayEquals(byteArray, deserializedInputStream.readAllBytes())
        }
    }

    @Test
    fun fullFile() {
        testResource().openStream().use {
            val deserializedInputStream = serializeDeserialize(it)
            assertArrayEquals(testResource().readBytes(), deserializedInputStream.readAllBytes())
        }
    }

    @Test
    fun partReadFile() {
        testResource().openStream().use {
            // Read 1 byte
            val bytesToDrop = 1
            it.readNBytes(bytesToDrop)

            val deserializedInputStream = serializeDeserialize(it)
            assertArrayEquals(testResource().readBytes().drop(bytesToDrop).toByteArray(), deserializedInputStream.readAllBytes())
        }
    }


    private fun testResource(): URL {
        // Read any file from test resources
        val resource = EvolvabilityTests::class.java.getResource("${EnumEvolveTests::class.java.simpleName}.changedOrdinality")
        assertNotNull(resource)
        return resource
    }

}