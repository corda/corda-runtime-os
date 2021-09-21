package net.corda.internal.serialization.amqp.custom

import net.corda.internal.serialization.amqp.ReusableSerialiseDeserializeAssert.Companion.serializeDeserialize
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

class StringBufferTest {
    @Test
    fun empty() {
        val instance = StringBuffer()
        val deserialize = serializeDeserialize(instance)
        checkStringBuffer(instance, deserialize)
    }

    @Test
    fun withContent() {
        val instance = StringBuffer("TEST")
        val deserialize = serializeDeserialize(instance)
        checkStringBuffer(instance, deserialize)
    }

    private fun checkStringBuffer(expected: StringBuffer, actual: StringBuffer) {
        Assertions.assertAll(
            { assertEquals(expected.toString(), actual.toString()) },
            { assertEquals(StringBuffer::class.java, actual.javaClass) }
        )
    }
}
