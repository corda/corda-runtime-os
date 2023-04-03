package net.corda.internal.serialization.amqp

import net.corda.base.internal.OpaqueBytesSubSequence
import net.corda.internal.serialization.amqp.helper.testSerializationContext
import net.corda.internal.serialization.amqp.testutils.serialize
import net.corda.internal.serialization.amqp.testutils.testDefaultFactoryNoEvolution
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test
import java.io.NotSerializableException

class AmqpMagicTests {
    @Test
    fun valueIsAsExpected() {
        Assertions.assertArrayEquals("corda".toByteArray() + byteArrayOf(4, 0), amqpMagic.getBytes())
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
                OpaqueBytesSubSequence(editedBytes, 0, editedBytes.size),
                String::class.java,
                testSerializationContext
            )
        }

        // Check exception message
        Assertions.assertEquals("Serialization header does not match.", exception.message)
    }
}
