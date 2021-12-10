package net.corda.libs.permissions.storage.writer.impl

import java.util.concurrent.CompletableFuture
import net.corda.data.permissions.management.PermissionManagementRequest
import net.corda.data.permissions.management.PermissionManagementResponse
import net.corda.data.permissions.management.user.CreateUserRequest
import net.corda.libs.permissions.storage.writer.impl.role.RoleWriter
import net.corda.libs.permissions.storage.writer.impl.user.UserWriter
import net.corda.v5.base.concurrent.getOrThrow
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

class PermissionStorageWriterProcessorImplTest {

    private val createUserRequest = CreateUserRequest().apply {
        fullName = "Dan Newton"
        loginName = "lankydan"
        enabled = true
    }

    private val creatorUserId = "creatorUserId"
    private val avroUser = net.corda.data.permissions.User()

    private val userWriter = mock<UserWriter>()
    private val roleWriter = mock<RoleWriter>()

    private val processor = PermissionStorageWriterProcessorImpl(mock(), userWriter, roleWriter)

    @Test
    fun `receiving invalid request completes exceptionally`() {
        val future = CompletableFuture<PermissionManagementResponse>()
        processor.onNext(
            request = PermissionManagementRequest().apply {
                request = Unit
                requestUserId = creatorUserId
            },
            respFuture = future
        )
        assertThrows<IllegalArgumentException> { future.getOrThrow() }
        verify(userWriter, never()).createUser(any())
        verify(roleWriter, never()).createRole(any())
    }

    @Test
    fun `receiving CreateUserRequest calls userWriter to create user and completes future`() {
        whenever(userWriter.createUser(createUserRequest)).thenReturn(avroUser)

        val future = CompletableFuture<PermissionManagementResponse>()
        processor.onNext(
            request = PermissionManagementRequest().apply {
                request = createUserRequest
                requestUserId = creatorUserId
            },
            respFuture = future
        )
        assertTrue(future.getOrThrow().response is net.corda.data.permissions.User)
        (future.getOrThrow().response as? net.corda.data.permissions.User)?.let { response ->
            assertEquals(avroUser, response)
            assertEquals(response.enabled, createUserRequest.enabled)
        }
    }

    @Test
    fun `receiving CreateUserRequest calls userWriter and catches exception and completes future exceptionally`() {
        whenever(userWriter.createUser(createUserRequest)).thenThrow(IllegalArgumentException("Entity manager error."))

        val future = CompletableFuture<PermissionManagementResponse>()
        processor.onNext(
            request = PermissionManagementRequest().apply {
                request = createUserRequest
                requestUserId = creatorUserId
            },
            respFuture = future
        )
        val e = assertThrows<IllegalArgumentException> { future.getOrThrow() }
        assertEquals("Entity manager error.", e.message)
    }
}