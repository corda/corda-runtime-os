package net.corda.libs.permissions.storage.writer.impl

import net.corda.data.permissions.PermissionType
import java.util.concurrent.CompletableFuture
import net.corda.data.permissions.management.PermissionManagementRequest
import net.corda.data.permissions.management.PermissionManagementResponse
import net.corda.data.permissions.management.permission.CreatePermissionRequest
import net.corda.data.permissions.management.role.CreateRoleRequest
import net.corda.data.permissions.management.user.CreateUserRequest
import net.corda.libs.permissions.storage.reader.PermissionStorageReader
import net.corda.libs.permissions.storage.writer.impl.permission.PermissionWriter
import net.corda.libs.permissions.storage.writer.impl.role.RoleWriter
import net.corda.libs.permissions.storage.writer.impl.user.UserWriter
import net.corda.v5.base.concurrent.getOrThrow
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.mockito.kotlin.any
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

        assertThrows<IllegalArgumentException> { future.getOrThrow() }

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
    fun `receiving CreateUserRequest calls userWriter and catches exception and does not publish and completes future exceptionally`() {
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

        val e = assertThrows<IllegalArgumentException> { future.getOrThrow() }
        assertEquals("Entity manager error.", e.message)
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
    fun `receiving CreateRoleRequest calls roleWriter and catches exception and does not publish and completes future exceptionally`() {
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

        val e = assertThrows<IllegalArgumentException> { future.getOrThrow() }
        assertEquals("Entity manager error.", e.message)
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

        val response = future.getOrThrow().response
        assertTrue(response is AvroPermission)
        (response as? AvroPermission)?.let { permission ->
            assertEquals(avroPermission, permission)
        }
    }

    @Test
    fun `receiving CreatePermissionRequest calls permissionWriter and completes future exceptionally`() {
        val message = "Entity manager error."
        whenever(permissionWriter.createPermission(createPermissionRequest, creatorUserId, null)).thenThrow(
            IllegalArgumentException(
                message
            )
        )

        val future = CompletableFuture<PermissionManagementResponse>()
        processor.onNext(
            request = PermissionManagementRequest().apply {
                request = createPermissionRequest
                requestUserId = creatorUserId
            },
            respFuture = future
        )

        val e = assertThrows<IllegalArgumentException> { future.getOrThrow() }
        assertEquals(message, e.message)
    }
}