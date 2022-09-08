package net.corda.internal.serialization.amqp.custom

import net.corda.internal.serialization.amqp.ReusableSerialiseDeserializeAssert.Companion.factory
import net.corda.internal.serialization.amqp.ReusableSerialiseDeserializeAssert.Companion.serializeDeserialize
import net.corda.internal.serialization.amqp.SerializationOutput
import net.corda.internal.serialization.amqp.TypeNotation
import net.corda.internal.serialization.amqp.helper.testSerializationContext
import net.corda.v5.base.exceptions.CordaRuntimeException
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.assertAll
import org.junit.jupiter.api.fail
import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Test

class ThrowableTest {
    class TestException(override val message: String) : CordaRuntimeException(message)

    @Test
    fun cordaRuntimeException() {
        val instance = CordaRuntimeException("TEST")
        val deserialize = serializeDeserialize<Throwable>(instance)

        assertAll(
            { assertSame(instance.javaClass, deserialize.javaClass) },
            { assertEquals(instance.message, deserialize.message) },
            { assertStackTraceEquals(instance.stackTrace, deserialize.stackTrace) },
            { assertEquals(instance.cause, deserialize.cause) },
            { assertArrayEquals(instance.suppressed, deserialize.suppressed) },
        )
    }

    @Test
    fun customCordaRuntimeException() {
        val instance = TestException("TEST")
        val deserialize = serializeDeserialize<Throwable>(instance)

        assertAll(
            { assertSame(instance.javaClass, deserialize.javaClass) },
            { assertEquals(instance.message, deserialize.message) },
            { assertStackTraceEquals(instance.stackTrace, deserialize.stackTrace) },
            { assertEquals(instance.cause, deserialize.cause) },
            { assertArrayEquals(instance.suppressed, deserialize.suppressed) },
        )
    }
    
    @Test
    fun nullPointerException() {
        val instance = java.lang.NullPointerException("TEST")
        val deserialize = serializeDeserialize<Throwable>(instance)

        assertAll(
            { assertSame(CordaRuntimeException::class.java, deserialize.javaClass) },
            { assertEquals("java.lang.NullPointerException: TEST", deserialize.message) },
            { assertStackTraceEquals(instance.stackTrace, deserialize.stackTrace) },
            { assertEquals(instance.cause, deserialize.cause) },
            { assertArrayEquals(instance.suppressed, deserialize.suppressed) },
        )
    }

    @Test
    fun testSerializerIsRegisteredForSubclass() {
        val ex = TestException("Can't catch this!")

        val schemas = SerializationOutput(factory).serializeAndReturnSchema(ex, testSerializationContext)
        assertThat(schemas.schema.types.map(TypeNotation::name)).contains(ex::class.java.name)

        val serializer = factory.findCustomSerializer(ex::class.java, ex::class.java)
            ?: fail("No custom serializer found")
        assertThat(serializer.type).isSameAs(ex::class.java)
    }

    private fun assertStackTraceEquals(expected: Array<StackTraceElement>, actual: Array<StackTraceElement>) {
        assertThat(actual).hasSameSizeAs(expected)
        actual.forEachIndexed { idx, elt ->
            assertTrue(elt.isEqualTo(expected[idx]), "Element $idx: expected ${expected[idx]} but was $elt")
        }
    }

    private fun StackTraceElement.isEqualTo(other: StackTraceElement): Boolean {
        return className == other.className
                && methodName == other.methodName
                && fileName == other.fileName
                && lineNumber == other.lineNumber
    }
}
