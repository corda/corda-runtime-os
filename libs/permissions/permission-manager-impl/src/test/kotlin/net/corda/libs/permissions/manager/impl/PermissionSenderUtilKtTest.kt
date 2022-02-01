package net.corda.libs.permissions.manager.impl

import java.lang.IllegalArgumentException
import java.time.Duration
import java.util.concurrent.CompletableFuture
import java.util.concurrent.TimeoutException
import net.corda.data.ExceptionEnvelope
import net.corda.data.permissions.management.PermissionManagementRequest
import net.corda.data.permissions.management.PermissionManagementResponse
import net.corda.libs.permissions.manager.exception.RemotePermissionManagementException
import net.corda.libs.permissions.manager.exception.UnexpectedPermissionResponseException
import net.corda.messaging.api.publisher.RPCSender
import net.corda.v5.base.concurrent.getOrThrow
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

internal class PermissionSenderUtilKtTest {

    private val rpcSender = mock<RPCSender<PermissionManagementRequest, PermissionManagementResponse>>()
    private val timeout = Duration.ofMillis(100)
    private val permissionManagementRequest = mock<PermissionManagementRequest>()
    private val future = mock<CompletableFuture<PermissionManagementResponse>>()

    data class TestUser(val id: String)
    data class SomeOtherType(val id: String)

    @Test
    internal fun `send permission write request times out`() {
        whenever(rpcSender.sendRequest(permissionManagementRequest)).thenReturn(future)
        whenever(future.getOrThrow(timeout)).thenThrow(TimeoutException("timed out."))

        val e = assertThrows<TimeoutException> {
            sendPermissionWriteRequest(rpcSender, timeout, permissionManagementRequest)
        }

        assertEquals("timed out.", e.message)

    }

    @Test
    internal fun `send permission write request`() {
        val testUser = TestUser("1")

        whenever(rpcSender.sendRequest(permissionManagementRequest)).thenReturn(future)
        whenever(future.getOrThrow(timeout)).thenReturn(PermissionManagementResponse(testUser))

        val result: TestUser = sendPermissionWriteRequest(rpcSender, timeout, permissionManagementRequest)

        assertEquals(testUser, result)
    }

    @Test
    internal fun `send permission write request receives some other type`() {
        val otherType = SomeOtherType("1")

        whenever(rpcSender.sendRequest(permissionManagementRequest)).thenReturn(future)
        whenever(future.getOrThrow(timeout)).thenReturn(PermissionManagementResponse(otherType))

        val e = assertThrows<UnexpectedPermissionResponseException> {
            sendPermissionWriteRequest(rpcSender, timeout, permissionManagementRequest)
        }

        assertEquals("Unknown response type for permission management request: ${SomeOtherType::class.java.name}", e.message)
    }

    @Test
    internal fun `send permission write request responds with exception envelope`() {
        whenever(rpcSender.sendRequest(permissionManagementRequest)).thenReturn(future)
        whenever(future.getOrThrow(timeout)).thenReturn(
            PermissionManagementResponse(ExceptionEnvelope(IllegalArgumentException::class.java.name, "illegal arg."))
        )

        val e = assertThrows<RemotePermissionManagementException> {
            sendPermissionWriteRequest(rpcSender, timeout, permissionManagementRequest)
        }

        assertEquals(IllegalArgumentException::class.java.name, e.exceptionType)
        assertEquals("illegal arg.", e.message)
    }
}