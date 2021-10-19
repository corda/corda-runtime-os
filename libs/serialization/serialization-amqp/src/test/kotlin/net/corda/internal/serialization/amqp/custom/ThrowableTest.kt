package net.corda.internal.serialization.amqp.custom

import net.corda.internal.serialization.amqp.ReusableSerialiseDeserializeAssert.Companion.serializeDeserialize
import net.corda.v5.base.exceptions.CordaRuntimeException
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

class ThrowableTest {

    @Test
    fun cordaRuntimeException() {
        val instance = CordaRuntimeException("TEST")
        val deserialize = serializeDeserialize<Throwable>(instance)

        Assertions.assertAll(
            { assertEquals<Class<Throwable>>(instance.javaClass, deserialize.javaClass) },
            { assertEquals(instance.message, deserialize.message) },
            { Assertions.assertArrayEquals(emptyArray(), deserialize.stackTrace) },
            { assertEquals(instance.cause, deserialize.cause) },
            { Assertions.assertArrayEquals(instance.suppressed, deserialize.suppressed) },
        )
    }

    @Test
    fun customCordaRuntimeException() {

        class TestException(override val message: String) : CordaRuntimeException(message)

        val instance = TestException("TEST")
        val deserialize = serializeDeserialize<Throwable>(instance)

        Assertions.assertAll(
            { assertEquals<Class<Throwable>>(instance.javaClass, deserialize.javaClass) },
            { assertEquals(instance.message, deserialize.message) },
            { Assertions.assertArrayEquals(emptyArray(), deserialize.stackTrace) },
            { assertEquals(instance.cause, deserialize.cause) },
            { Assertions.assertArrayEquals(instance.suppressed, deserialize.suppressed) },
        )
    }
    
    @Test
    fun nullPointerException() {
        val instance = java.lang.NullPointerException("TEST")
        val deserialize = serializeDeserialize<Throwable>(instance)

        Assertions.assertAll(
            { assertEquals<Class<*>>(CordaRuntimeException::class.java, deserialize.javaClass) },
            { assertEquals("java.lang.NullPointerException: TEST", deserialize.message) },
            { Assertions.assertArrayEquals(emptyArray(), deserialize.stackTrace) },
            { assertEquals(instance.cause, deserialize.cause) },
            { Assertions.assertArrayEquals(instance.suppressed, deserialize.suppressed) },
        )
    }
}
