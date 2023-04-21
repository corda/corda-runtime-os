package net.corda.internal.serialization.amqp.custom

import net.corda.internal.serialization.amqp.ReusableSerialiseDeserializeAssert.Companion.serializeDeserialize
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

class StackTraceElementTest {
    @Test
    fun cordaRuntimeException() {
        val instance = Throwable().stackTrace.first()
        val stackTraceElement = serializeDeserialize(instance)
        Assertions.assertAll(
            { assertEquals(StackTraceElement::class.java, stackTraceElement.javaClass) },
            { assertEquals(instance.className, stackTraceElement.className) },
            { assertEquals(instance.methodName, stackTraceElement.methodName) },
            { assertEquals(instance.fileName, stackTraceElement.fileName) },
            { assertEquals(instance.lineNumber, stackTraceElement.lineNumber) }
        )
    }
}