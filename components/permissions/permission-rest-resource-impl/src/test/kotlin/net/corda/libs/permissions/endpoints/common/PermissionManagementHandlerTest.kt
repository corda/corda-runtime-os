package net.corda.libs.permissions.endpoints.common

import net.corda.libs.permissions.manager.PermissionManager
import net.corda.libs.permissions.manager.exception.RemotePermissionManagementException
import net.corda.libs.permissions.manager.exception.UnexpectedPermissionResponseException
import net.corda.messaging.api.exception.CordaRPCAPIPartitionException
import net.corda.messaging.api.exception.CordaRPCAPIResponderException
import net.corda.messaging.api.exception.CordaRPCAPISenderException
import net.corda.rest.exception.InternalServerException
import net.corda.rest.exception.ServiceUnavailableException
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.mockito.kotlin.mock
import org.slf4j.Logger
import java.util.concurrent.TimeoutException

internal class PermissionManagementHandlerTest {

    private val permissionManager = mock<PermissionManager>()
    private val logger = mock<Logger>()

    @Test
    fun `test TimeoutException returns InternalServerException`() {
        val e = assertThrows<InternalServerException> {
            withPermissionManager(permissionManager, logger) {
                throw TimeoutException("Timed out.")
            }
        }
        assertEquals("Permission management operation timed out.", e.message)
        assertEquals(0, e.details.size)
    }

    @Test
    fun `test CordaRPCAPIResponderException with no throwable returns InternalServerException`() {
        val e = assertThrows<InternalServerException>("Internal server error.") {
            withPermissionManager(permissionManager, logger) {
                throw CordaRPCAPIResponderException("errorType", "Responder exception")
            }
        }
        assertEquals("Internal server error.", e.message)
        assertEquals(0, e.details.size)
        assertEquals(CordaRPCAPIResponderException::class.java.name, e.exceptionDetails!!.cause)
        assertEquals("Responder exception", e.exceptionDetails!!.reason)
    }

    @Test
    fun `test CordaRPCAPIResponderException with throwable returns InternalServerException`() {
        val e = assertThrows<InternalServerException> {
            withPermissionManager(permissionManager, logger) {
                throw CordaRPCAPIResponderException(
                    "errorType",
                    "Responder exception",
                    IllegalArgumentException("illegal arg")
                )
            }
        }
        assertEquals("Internal server error.", e.message)
        assertEquals(0, e.details.size)
        assertEquals("java.lang.IllegalArgumentException", e.exceptionDetails!!.cause)
        assertEquals("illegal arg", e.exceptionDetails!!.reason)
    }

    @Test
    fun `test CordaRPCAPISenderException with throwable returns InternalServerException`() {
        val e = assertThrows<InternalServerException> {
            withPermissionManager(permissionManager, logger) {
                throw CordaRPCAPISenderException("Sender exception", IllegalArgumentException("illegal arg"))
            }
        }
        assertEquals("Internal server error.", e.message)
        assertEquals(0, e.details.size)
        assertEquals("java.lang.IllegalArgumentException", e.exceptionDetails!!.cause)
        assertEquals("illegal arg", e.exceptionDetails!!.reason)
    }

    @Test
    fun `test CordaRPCAPISenderException with no throwable returns InternalServerException`() {
        val e = assertThrows<InternalServerException> {
            withPermissionManager(permissionManager, logger) {
                throw CordaRPCAPISenderException("Sender exception")
            }
        }
        assertEquals("Internal server error.", e.message)
        assertEquals(0, e.details.size)
        assertEquals(CordaRPCAPISenderException::class.java.name, e.exceptionDetails!!.cause)
        assertEquals("Sender exception", e.exceptionDetails!!.reason)
    }

    @Test
    fun `test UnexpectedPermissionResponseException returns InternalServerException`() {
        val e = assertThrows<InternalServerException> {
            withPermissionManager(permissionManager, logger) {
                throw UnexpectedPermissionResponseException("unexpected exception")
            }
        }
        assertEquals("Internal server error.", e.message)
        assertEquals(0, e.details.size)
        assertEquals(UnexpectedPermissionResponseException::class.java.name, e.exceptionDetails!!.cause)
        assertEquals("unexpected exception", e.exceptionDetails!!.reason)
    }

    @Test
    fun `test CordaRPCAPIPartitionException returns ServiceUnavailableException`() {
        val e = assertThrows<ServiceUnavailableException> {
            withPermissionManager(permissionManager, logger) {
                throw CordaRPCAPIPartitionException("Repartition event.")
            }
        }
        assertEquals("Error waiting for permission management response: Repartition Event!", e.message)
        assertEquals(0, e.details.size)
        assertEquals(CordaRPCAPIPartitionException::class.java.name, e.exceptionDetails!!.cause)
        assertEquals("Repartition event.", e.exceptionDetails!!.reason)
    }

    @Test
    fun `test RemotePermissionManagementException returns exception type and message`() {
        val e = assertThrows<InternalServerException> {
            withPermissionManager(permissionManager, logger) {
                throw RemotePermissionManagementException("javax.persistence.OptimisticLockException", "JPA exception handled")
            }
        }
        assertEquals("Internal server error.", e.message)
        assertEquals(0, e.details.size)
        assertEquals("javax.persistence.OptimisticLockException", e.exceptionDetails!!.cause)
        assertEquals("JPA exception handled", e.exceptionDetails!!.reason)
    }

    @Suppress("TooGenericExceptionThrown")
    @Test
    fun `test random exception returns UnexpectedErrorException`() {
        val e = assertThrows<InternalServerException> {
            withPermissionManager(permissionManager, logger) {
                throw Exception("random exception")
            }
        }
        assertEquals("Unexpected permission management error occurred.", e.message)
        assertEquals(0, e.details.size)
        assertEquals(Exception::class.java.name, e.exceptionDetails!!.cause)
        assertEquals("random exception", e.exceptionDetails!!.reason)
    }
}
