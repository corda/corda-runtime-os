package net.corda.libs.permissions.endpoints.common

import java.util.concurrent.TimeoutException
import net.corda.httprpc.exception.InternalServerException
import net.corda.httprpc.exception.UnexpectedErrorException
import net.corda.libs.permissions.manager.PermissionManager
import net.corda.libs.permissions.manager.exception.RemotePermissionManagementException
import net.corda.libs.permissions.manager.exception.UnexpectedPermissionResponseException
import net.corda.messaging.api.exception.CordaRPCAPIResponderException
import net.corda.messaging.api.exception.CordaRPCAPISenderException
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.mockito.kotlin.mock
import org.slf4j.Logger

internal class PermissionManagementHandlerTest {

    private val permissionManager = mock<PermissionManager>()
    private val logger = mock<Logger>()

    @Test
    fun `test TimeoutException returns InternalServerException`() {

        val e = assertThrows<InternalServerException>("Permission management operation timed out.") {
            withPermissionManager(permissionManager, logger){
                throw TimeoutException("Timed out.")
            }
        }
        assertEquals(0, e.details.size)
    }

    @Test
    fun `test CordaRPCAPIResponderException with no throwable returns InternalServerException`() {

        val e = assertThrows<InternalServerException>("Internal server error.") {
            withPermissionManager(permissionManager, logger){
                throw CordaRPCAPIResponderException("Responder exception")
            }
        }
        assertEquals(2, e.details.size)
        assertEquals(CordaRPCAPIResponderException::class.java.name, e.details["cause"])
        assertEquals("Responder exception", e.details["reason"])
    }

    @Test
    fun `test CordaRPCAPIResponderException with throwable returns InternalServerException`() {

        val e = assertThrows<InternalServerException>("Internal server error.") {
            withPermissionManager(permissionManager, logger){
                throw CordaRPCAPIResponderException("Responder exception", IllegalArgumentException("illegal arg"))
            }
        }
        assertEquals(2, e.details.size)
        assertEquals("java.lang.IllegalArgumentException", e.details["cause"])
        assertEquals("illegal arg", e.details["reason"])
    }

    @Test
    fun `test CordaRPCAPISenderException with throwable returns InternalServerException`() {

        val e = assertThrows<InternalServerException>("Internal server error.") {
            withPermissionManager(permissionManager, logger){
                throw CordaRPCAPISenderException("Sender exception", IllegalArgumentException("illegal arg"))
            }
        }
        assertEquals(2, e.details.size)
        assertEquals("java.lang.IllegalArgumentException", e.details["cause"])
        assertEquals("illegal arg", e.details["reason"])
    }

    @Test
    fun `test CordaRPCAPISenderException with no throwable returns InternalServerException`() {

        val e = assertThrows<InternalServerException>("Internal server error.") {
            withPermissionManager(permissionManager, logger){
                throw CordaRPCAPISenderException("Sender exception")
            }
        }
        assertEquals(2, e.details.size)
        assertEquals(CordaRPCAPISenderException::class.java.name, e.details["cause"])
        assertEquals("Sender exception", e.details["reason"])
    }

    @Test
    fun `test UnexpectedPermissionResponseException returns InternalServerException`() {

        val e = assertThrows<InternalServerException>("Internal server error.") {
            withPermissionManager(permissionManager, logger){
                throw UnexpectedPermissionResponseException("unexpected exception")
            }
        }
        assertEquals(2, e.details.size)
        assertEquals(UnexpectedPermissionResponseException::class.java.name, e.details["cause"])
        assertEquals("unexpected exception", e.details["reason"])
    }

    @Test
    fun `test RemotePermissionManagementException returns exception type and message`() {

        val e = assertThrows<InternalServerException>("JPA exception handled") {
            withPermissionManager(permissionManager, logger){
                throw RemotePermissionManagementException("javax.persistence.OptimisticLockException", "JPA exception handled")
            }
        }
        assertEquals(2, e.details.size)
        assertEquals("javax.persistence.OptimisticLockException", e.details["cause"])
        assertEquals("JPA exception handled", e.details["reason"])
    }

    @Suppress("TooGenericExceptionThrown")
    @Test
    fun `test random exception returns UnexpectedErrorException`() {

        val e = assertThrows<UnexpectedErrorException>("Unexpected permission management error occurred.") {
            withPermissionManager(permissionManager, logger){
                throw Exception("random exception")
            }
        }
        assertEquals(2, e.details.size)
        assertEquals(Exception::class.java.name, e.details["cause"])
        assertEquals("random exception", e.details["reason"])
    }
}