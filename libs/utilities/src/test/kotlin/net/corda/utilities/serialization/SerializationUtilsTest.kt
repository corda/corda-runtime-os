package net.corda.utilities.serialization

import net.corda.v5.base.exceptions.CordaRuntimeException
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertDoesNotThrow
import org.junit.jupiter.api.assertThrows


class SerializationUtilsTest {

    @Test
    fun `test wrapWithNullErrorHandling - null value`() {
        assertThrows<CordaRuntimeException>(message = "Failed - null") {
            wrapWithNullErrorHandling({ throw CordaRuntimeException("Failed - null") }) { null }
        }
    }

    @Test
    fun `test wrapWithNullErrorHandling - exception thrown`() {
        val ex = assertThrows<CordaRuntimeException>(message = "Failed - Throws") {
            wrapWithNullErrorHandling({
                throw CordaRuntimeException("Failed - Throws")
            }) { throw TestException("sub message") }
        }

        assertEquals(ex.cause!!::class.java, TestException::class.java)
        assertEquals(ex.cause!!.message, "sub message")
    }

    @Test
    fun `test wrapWithNullErrorHandling - no error`() {
        assertDoesNotThrow {
            assertEquals(
                "return value",
                wrapWithNullErrorHandling({ throw CordaRuntimeException("never will be thrown") }) {
                    "return value"
                })
        }
    }

}

class TestException(message: String) : Exception(message)
