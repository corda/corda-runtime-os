package net.corda.internal.serialization.amqp

import net.corda.internal.serialization.amqp.testutils.serialize
import net.corda.internal.serialization.amqp.testutils.testDefaultFactoryNoEvolution
import net.corda.internal.serialization.amqp.testutils.testSerializationContext
import net.corda.v5.base.types.ByteSequence
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test
import java.io.NotSerializableException

class AmqpMagicTests {
    @Test
    fun valueIsAsExpected() {
        Assertions.assertArrayEquals("corda".toByteArray() + byteArrayOf(3, 0), amqpMagic.bytes)
    }

    @Test
    fun throwsExceptionWhenMagicDifferent() {

        // Prepare some correct serialisation input
        val factory = testDefaultFactoryNoEvolution()
        val serializedBytes = SerializationOutput(factory).serialize("")
        val editedBytes = serializedBytes.bytes

        // Change the version back to 1 (matching Corda 4)
        val versionOffset = 5
        editedBytes[versionOffset] = 1

        // Check that NotSerializableException is thrown when trying to deserialize
        val exception = Assertions.assertThrows(NotSerializableException::class.java) {
            DeserializationInput(factory).deserialize(
                ByteSequence.of(editedBytes),
                String::class.java,
                testSerializationContext
            )
        }

        // Check exception message
        Assertions.assertEquals("Serialization header does not match.", exception.message)
    }
}
