package net.corda.libs.permissions.storage.writer.impl

import java.time.Instant
import net.corda.data.permissions.PermissionType
import java.util.concurrent.CompletableFuture
import net.corda.data.ExceptionEnvelope
import net.corda.data.permissions.ChangeDetails
import net.corda.data.permissions.RoleAssociation
import net.corda.data.permissions.management.PermissionManagementRequest
import net.corda.data.permissions.management.PermissionManagementResponse
import net.corda.data.permissions.management.permission.CreatePermissionRequest
import net.corda.data.permissions.management.role.CreateRoleRequest
import net.corda.data.permissions.management.user.AddRoleToUserRequest
import net.corda.data.permissions.management.user.CreateUserRequest
import net.corda.data.permissions.management.user.RemoveRoleFromUserRequest
import net.corda.libs.permissions.storage.reader.PermissionStorageReader
import net.corda.libs.permissions.storage.writer.impl.permission.PermissionWriter
import net.corda.libs.permissions.storage.writer.impl.role.RoleWriter
import net.corda.libs.permissions.storage.writer.impl.user.UserWriter
import net.corda.v5.base.concurrent.getOrThrow
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import net.corda.data.permissions.User as AvroUser
import net.corda.data.permissions.Role as AvroRole
import net.corda.data.permissions.Permission as AvroPermission

class PermissionStorageWriterProcessorImplTest {

    private val createUserRequest = CreateUserRequest().apply {
        fullName = "Dan Newton"
        loginName = "lankydan"
        enabled = true
    }
    private val createRoleRequest = CreateRoleRequest("role1", null)

    private val createPermissionRequest = CreatePermissionRequest(PermissionType.ALLOW,
        "permissionString", null)

    private val creatorUserId = "creatorUserId"
    private val avroUser = AvroUser().apply {
        enabled = true
    }
    private val avroRole = AvroRole()

    private val avroPermission = AvroPermission()

    private val userWriter = mock<UserWriter>()
    private val roleWriter = mock<RoleWriter>()
    private val permissionWriter = mock<PermissionWriter>()
    private val permissionStorageReader = mock<PermissionStorageReader>()

    private val processor = PermissionStorageWriterProcessorImpl(permissionStorageReader, userWriter, roleWriter, permissionWriter)

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

        val result = future.getOrThrow()
        assertTrue(result.response is ExceptionEnvelope)
        val exception = result.response as ExceptionEnvelope
        assertEquals(IllegalArgumentException::class.java.name, exception.errorType)
        assertEquals("Received invalid permission request type.", exception.errorMessage)

        verify(userWriter, never()).createUser(any(), eq(creatorUserId))
        verify(roleWriter, never()).createRole(any(), eq(creatorUserId))
        verify(permissionStorageReader, never()).publishNewUser(any())
        verify(permissionStorageReader, never()).publishNewRole(any())
    }

    @Test
    fun `receiving CreateUserRequest calls userWriter to create user and publishes user and completes future`() {
        whenever(userWriter.createUser(createUserRequest, creatorUserId)).thenReturn(avroUser)

        val future = CompletableFuture<PermissionManagementResponse>()
        processor.onNext(
            request = PermissionManagementRequest().apply {
                request = createUserRequest
                requestUserId = creatorUserId
            },
            respFuture = future
        )

        verify(permissionStorageReader, times(1)).publishNewUser(avroUser)

        val response = future.getOrThrow().response
        assertTrue(response is AvroUser)
        (response as? AvroUser)?.let { user ->
            assertEquals(avroUser, user)
            assertEquals(user.enabled, createUserRequest.enabled)
        }
    }

    @Test
    fun `create user receives exception and completes future with exception in the response`() {
        whenever(userWriter.createUser(createUserRequest, creatorUserId)).thenThrow(IllegalArgumentException("Entity manager error."))

        val future = CompletableFuture<PermissionManagementResponse>()
        processor.onNext(
            request = PermissionManagementRequest().apply {
                request = createUserRequest
                requestUserId = creatorUserId
            },
            respFuture = future
        )

        verify(permissionStorageReader, times(0)).publishNewUser(any())

        val result = future.getOrThrow()
        assertTrue(result.response is ExceptionEnvelope)
        val exception = result.response as ExceptionEnvelope
        assertEquals(IllegalArgumentException::class.java.name, exception.errorType)
        assertEquals("Entity manager error.", exception.errorMessage)
    }

    @Test
    fun `receiving CreateRoleRequest calls roleWriter to create role and publishes role and completes future`() {
        whenever(roleWriter.createRole(createRoleRequest, creatorUserId)).thenReturn(avroRole)

        val future = CompletableFuture<PermissionManagementResponse>()
        processor.onNext(
            request = PermissionManagementRequest().apply {
                request = createRoleRequest
                requestUserId = creatorUserId
            },
            respFuture = future
        )

        verify(permissionStorageReader, times(1)).publishNewRole(avroRole)

        val response = future.getOrThrow().response
        assertTrue(response is AvroRole)
        (response as? AvroRole)?.let { role ->
            assertEquals(avroRole, role)
        }
    }

    @Test
    fun `create role with exception completes future with exception in response`() {
        whenever(roleWriter.createRole(createRoleRequest, creatorUserId)).thenThrow(IllegalArgumentException("Entity manager error."))

        val future = CompletableFuture<PermissionManagementResponse>()
        processor.onNext(
            request = PermissionManagementRequest().apply {
                request = createRoleRequest
                requestUserId = creatorUserId
            },
            respFuture = future
        )

        verify(permissionStorageReader, times(0)).publishNewRole(any())

        val result = future.getOrThrow()
        assertTrue(result.response is ExceptionEnvelope)
        val exception = result.response as ExceptionEnvelope
        assertEquals(IllegalArgumentException::class.java.name, exception.errorType)
        assertEquals("Entity manager error.", exception.errorMessage)
    }

    @Test
    fun `receiving CreatePermissionRequest calls permissionWriter to create permission and publishes permission and completes future`() {
        whenever(permissionWriter.createPermission(createPermissionRequest, creatorUserId, null)).thenReturn(
            avroPermission
        )

        val future = CompletableFuture<PermissionManagementResponse>()
        processor.onNext(
            request = PermissionManagementRequest().apply {
                request = createPermissionRequest
                requestUserId = creatorUserId
            },
            respFuture = future
        )

        val result = future.getOrThrow()
        val response = result.response
        assertTrue(response is AvroPermission)
        (response as? AvroPermission)?.let { permission ->
            assertEquals(avroPermission, permission)
        }
    }

    @Test
    fun `receiving CreatePermissionRequest calls permissionWriter and completes future with exception in the response`() {
        val capture = argumentCaptor<PermissionManagementResponse>()
        val message = "Entity manager error."
        whenever(permissionWriter.createPermission(createPermissionRequest, creatorUserId, null))
            .thenThrow(IllegalArgumentException(message))

        val future = mock<CompletableFuture<PermissionManagementResponse>>()
        processor.onNext(
            request = PermissionManagementRequest().apply {
                request = createPermissionRequest
                requestUserId = creatorUserId
            },
            respFuture = future
        )

        verify(permissionStorageReader, times(0)).publishNewPermission(any())
        verify(future, times(1)).complete(capture.capture())

        assertTrue(capture.firstValue.response is ExceptionEnvelope)
        val exception = capture.firstValue.response as ExceptionEnvelope
        assertEquals(IllegalArgumentException::class.java.name, exception.errorType)
        assertEquals(message, exception.errorMessage)
    }

    @Test
    fun `receiving AddRoleToUserRequest calls userWriter and publishes updated user`() {
        val capture = argumentCaptor<PermissionManagementResponse>()
        val avroUser = AvroUser().apply {
            id = "userId1"
            loginName = "userLogin1"
            enabled = true
            roleAssociations = listOf(RoleAssociation(ChangeDetails(Instant.now()), "roleId"))
        }

        val avroRequest = AddRoleToUserRequest("userLogin1", "roleId")
        val future = mock<CompletableFuture<PermissionManagementResponse>>()

        whenever(userWriter.addRoleToUser(avroRequest, creatorUserId)).thenReturn(avroUser)
        whenever(future.complete(capture.capture())).thenReturn(true)

        processor.onNext(
            request = PermissionManagementRequest().apply {
                request = avroRequest
                requestUserId = creatorUserId
            },
            respFuture = future
        )

        verify(permissionStorageReader, times(1)).publishUpdatedUser(avroUser)

        assertNotNull(capture.firstValue.response)
        assertEquals(avroUser, capture.firstValue.response)
    }

    @Test
    fun `receiving RemoveRoleFromUserRequest calls userWriter and publishes updated user`() {
        val avroUser = AvroUser().apply {
            id = "userId1"
            loginName = "userLogin1"
            enabled = true
        }

        val avroRequest = RemoveRoleFromUserRequest("userLogin1", "roleId")

        whenever(userWriter.removeRoleFromUser(avroRequest, creatorUserId)).thenReturn(avroUser)

        val future = CompletableFuture<PermissionManagementResponse>()
        processor.onNext(
            request = PermissionManagementRequest().apply {
                request = avroRequest
                requestUserId = creatorUserId
            },
            respFuture = future
        )

        verify(permissionStorageReader, times(1)).publishUpdatedUser(avroUser)

        val result = future.getOrThrow()
        val response = result.response
        assertTrue(response is AvroUser)
        (response as? AvroUser)?.let { user ->
            assertEquals(avroUser, user)
        }
    }

}