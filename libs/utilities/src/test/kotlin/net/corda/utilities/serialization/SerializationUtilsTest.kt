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
        val ex = assertThrows<TestException>(message = "Failed - Throws") {
            wrapWithNullErrorHandling(onErrorOrNull = {
                throw TestException("Failed - Throws", it)
            }) {
                throw CordaRuntimeException("This is the Cause")
            }
        }
        assertEquals(CordaRuntimeException::class.java, ex.cause!!.javaClass)
    }

    @Test
    fun `test wrapWithNullErrorHandling - no error`() {
        assertDoesNotThrow {
            assertEquals(
                "return value",
                wrapWithNullErrorHandling({ throw CordaRuntimeException("never will be thrown") }) {
                    "return value"
                }
            )
        }
    }
}

class TestException(message: String, cause: Exception) : Exception(message, cause)
