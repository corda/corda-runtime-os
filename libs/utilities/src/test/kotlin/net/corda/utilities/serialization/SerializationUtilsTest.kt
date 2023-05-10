package net.corda.utilities.serialization

import net.corda.v5.base.exceptions.CordaRuntimeException
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertDoesNotThrow
import org.junit.jupiter.api.assertThrows


class SerializationUtilsTest {

    @Test
    fun `test wrapWithNullErrorHandling - null value`() {
        assertThrows<CordaRuntimeException>(message = "Failed - null") {
            wrapWithNullErrorHandling("Failed - null", CordaRuntimeException::class.java) { null }
        }
    }

    @Test
    fun `test wrapWithNullErrorHandling - exception thrown`() {
        val ex = assertThrows<CordaRuntimeException>(message = "Failed - Throws") {
            wrapWithNullErrorHandling(
                "Failed - Throws",
                CordaRuntimeException::class.java
            ) { throw TestException("sub message") }
        }

        assertEquals(ex.cause!!::class.java, TestException::class.java)
        assertEquals(ex.cause!!.message, "sub message")
    }

    @Test
    fun `test wrapWithNullErrorHandling - no error`() {
        assertDoesNotThrow {
            assertEquals("return value", wrapWithNullErrorHandling("never will be thrown", CordaRuntimeException::class.java) {
                "return value"
            })
        }
    }

}

class TestException(message: String) : Exception(message)
