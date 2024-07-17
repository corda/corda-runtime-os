package net.corda.libs.permissions.storage.writer.impl

import net.corda.data.ExceptionEnvelope
import net.corda.data.permissions.ChangeDetails
import net.corda.data.permissions.PermissionType
import net.corda.data.permissions.Property
import net.corda.data.permissions.RoleAssociation
import net.corda.data.permissions.management.PermissionManagementRequest
import net.corda.data.permissions.management.PermissionManagementResponse
import net.corda.data.permissions.management.group.AddRoleToGroupRequest
import net.corda.data.permissions.management.group.ChangeGroupParentIdRequest
import net.corda.data.permissions.management.group.CreateGroupRequest
import net.corda.data.permissions.management.group.DeleteGroupRequest
import net.corda.data.permissions.management.group.RemoveRoleFromGroupRequest
import net.corda.data.permissions.management.permission.CreatePermissionRequest
import net.corda.data.permissions.management.role.CreateRoleRequest
import net.corda.data.permissions.management.user.AddPropertyToUserRequest
import net.corda.data.permissions.management.user.AddRoleToUserRequest
import net.corda.data.permissions.management.user.CreateUserRequest
import net.corda.data.permissions.management.user.DeleteUserRequest
import net.corda.data.permissions.management.user.RemovePropertyFromUserRequest
import net.corda.data.permissions.management.user.RemoveRoleFromUserRequest
import net.corda.libs.permissions.storage.reader.PermissionStorageReader
import net.corda.libs.permissions.storage.writer.impl.group.GroupWriter
import net.corda.libs.permissions.storage.writer.impl.permission.PermissionWriter
import net.corda.libs.permissions.storage.writer.impl.role.RoleWriter
import net.corda.libs.permissions.storage.writer.impl.user.UserWriter
import net.corda.utilities.concurrent.getOrThrow
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
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
import java.time.Instant
import java.util.concurrent.CompletableFuture
import net.corda.data.permissions.Group as AvroGroup
import net.corda.data.permissions.Permission as AvroPermission
import net.corda.data.permissions.Role as AvroRole
import net.corda.data.permissions.User as AvroUser

class PermissionStorageWriterProcessorImplTest {

    private val createUserRequest = CreateUserRequest().apply {
        fullName = "Dan Newton"
        loginName = "lankydan"
        enabled = true
    }

    private val deleteUserRequest = DeleteUserRequest().apply {
        loginName = "lankydan"
    }
    private val createRoleRequest = CreateRoleRequest("role1", null)

    private val createPermissionRequest = CreatePermissionRequest(
        PermissionType.ALLOW,
        "permissionString",
        null
    )

    private val creatorUserId = "creatorUserId"
    private val avroUser = AvroUser().apply {
        enabled = true
        loginName = "lankydan"
    }
    private val avroRole = AvroRole()

    private val avroPermission = AvroPermission()

    private val userWriter = mock<UserWriter>()
    private val roleWriter = mock<RoleWriter>()
    private val groupWriter = mock<GroupWriter>()
    private val permissionWriter = mock<PermissionWriter>()
    private val permissionStorageReader = mock<PermissionStorageReader>()

    private val processor =
        PermissionStorageWriterProcessorImpl(
            { permissionStorageReader },
            userWriter,
            roleWriter,
            groupWriter,
            permissionWriter
        )

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
        assertEquals("Received invalid permission request type: kotlin.Unit", exception.errorMessage)

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
        whenever(
            userWriter.createUser(
                createUserRequest,
                creatorUserId
            )
        ).thenThrow(IllegalArgumentException("Entity manager error."))

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
    fun `receiving DeleteUserRequest calls userWriter to delete user and publishes null user and completes future`() {
        whenever(userWriter.deleteUser(deleteUserRequest, creatorUserId)).thenReturn(avroUser)

        val future = CompletableFuture<PermissionManagementResponse>()
        processor.onNext(
            request = PermissionManagementRequest().apply {
                request = deleteUserRequest
                requestUserId = creatorUserId
            },
            respFuture = future
        )

        verify(permissionStorageReader, times(1)).publishDeletedUser(avroUser.loginName)

        val response = future.getOrThrow().response
        assertTrue(response is AvroUser)
        (response as? AvroUser)?.let { user ->
            assertEquals(avroUser, user)
            assertEquals(user.loginName, deleteUserRequest.loginName)
        }
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
        whenever(
            roleWriter.createRole(
                createRoleRequest,
                creatorUserId
            )
        ).thenThrow(IllegalArgumentException("Entity manager error."))

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

    @Test
    fun `receiving AddPropertyToUserRequest calls userWriter and publishes updated user`() {
        val capture = argumentCaptor<PermissionManagementResponse>()
        val avroUser = AvroUser().apply {
            id = "userId1"
            loginName = "userLogin1"
            enabled = true
            properties = listOf(Property("uuid", 0, ChangeDetails(Instant.now()), "key1", "value1"))
        }

        val avroRequest = AddPropertyToUserRequest("userlogin1", mapOf("key1" to "value1"))
        val future = mock<CompletableFuture<PermissionManagementResponse>>()

        whenever(userWriter.addPropertyToUser(avroRequest, creatorUserId)).thenReturn(avroUser)
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
    fun `receiving RemovePropertyFromUserRequest calls userWriter and publishes updated user`() {
        val avroUser = AvroUser().apply {
            id = "userId1"
            loginName = "userLogin1"
            enabled = true
        }

        val avroRequest = RemovePropertyFromUserRequest("userLogin1", "key1")

        whenever(userWriter.removePropertyFromUser(avroRequest, creatorUserId)).thenReturn(avroUser)

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

    @Test
    fun `receiving CreateGroupRequest calls group writer and publishes new group`() {
        val createGroupRequest = CreateGroupRequest("New Group", "Parent Group ID")
        val avroGroup = AvroGroup().apply {
            id = "group-id"
            name = "New Group"
            parentGroupId = "Parent Group ID"
        }

        whenever(groupWriter.createGroup(createGroupRequest, creatorUserId)).thenReturn(avroGroup)

        val future = CompletableFuture<PermissionManagementResponse>()
        processor.onNext(
            request = PermissionManagementRequest().apply {
                request = createGroupRequest
                requestUserId = creatorUserId
            },
            respFuture = future
        )

        verify(permissionStorageReader, times(1)).publishNewGroup(avroGroup)

        val response = future.getOrThrow().response
        assertTrue(response is AvroGroup)
        (response as? AvroGroup)?.let { group ->
            assertEquals(avroGroup, group)
            assertEquals(group.name, createGroupRequest.groupName)
            assertEquals(group.parentGroupId, createGroupRequest.parentGroupId)
        }
    }

    @Test
    fun `create group receives exception and completes future with exception in response`() {
        val createGroupRequest = CreateGroupRequest("New Group", "Parent Group ID")
        whenever(groupWriter.createGroup(createGroupRequest, creatorUserId))
            .thenThrow(IllegalArgumentException("Group creation error."))

        val future = CompletableFuture<PermissionManagementResponse>()
        processor.onNext(
            request = PermissionManagementRequest().apply {
                request = createGroupRequest
                requestUserId = creatorUserId
            },
            respFuture = future
        )

        verify(permissionStorageReader, times(0)).publishNewGroup(any())

        val result = future.getOrThrow()
        assertTrue(result.response is ExceptionEnvelope)
        val exception = result.response as ExceptionEnvelope
        assertEquals(IllegalArgumentException::class.java.name, exception.errorType)
        assertEquals("Group creation error.", exception.errorMessage)
    }

    @Test
    fun `receiving ChangeGroupParentIdRequest calls group writer and publishes updated group`() {
        val changeGroupParentIdRequest = ChangeGroupParentIdRequest("group-id", "new-parent-id")
        val updatedAvroGroup = AvroGroup().apply {
            id = "group-id"
            name = "Test Group"
            parentGroupId = "new-parent-id"
        }

        whenever(groupWriter.changeParentGroup(changeGroupParentIdRequest, creatorUserId)).thenReturn(updatedAvroGroup)

        val future = CompletableFuture<PermissionManagementResponse>()
        processor.onNext(
            request = PermissionManagementRequest().apply {
                request = changeGroupParentIdRequest
                requestUserId = creatorUserId
            },
            respFuture = future
        )

        verify(permissionStorageReader, times(1)).publishUpdatedGroup(updatedAvroGroup)
        verify(permissionStorageReader, times(1)).reconcilePermissionSummaries()

        val response = future.getOrThrow().response
        assertTrue(response is AvroGroup)
        (response as? AvroGroup)?.let { group ->
            assertEquals(updatedAvroGroup, group)
            assertEquals(group.parentGroupId, changeGroupParentIdRequest.newParentGroupId)
        }
    }

    @Test
    fun `receiving AddRoleToGroupRequest calls group writer and publishes updated group`() {
        val addRoleToGroupRequest = AddRoleToGroupRequest("group-id", "role-id")
        val updatedAvroGroup = AvroGroup().apply {
            id = "group-id"
            name = "Test Group"
            roleAssociations = listOf(RoleAssociation(ChangeDetails(Instant.now()), "role-id"))
        }

        whenever(groupWriter.addRoleToGroup(addRoleToGroupRequest, creatorUserId)).thenReturn(updatedAvroGroup)

        val future = CompletableFuture<PermissionManagementResponse>()
        processor.onNext(
            request = PermissionManagementRequest().apply {
                request = addRoleToGroupRequest
                requestUserId = creatorUserId
            },
            respFuture = future
        )

        verify(permissionStorageReader, times(1)).publishUpdatedGroup(updatedAvroGroup)
        verify(permissionStorageReader, times(1)).reconcilePermissionSummaries()

        val response = future.getOrThrow().response
        assertTrue(response is AvroGroup)
        (response as? AvroGroup)?.let { group ->
            assertEquals(updatedAvroGroup, group)
            assertTrue(group.roleAssociations.any { it.roleId == addRoleToGroupRequest.roleId })
        }
    }

    @Test
    fun `receiving RemoveRoleFromGroupRequest calls group writer and publishes updated group`() {
        val removeRoleFromGroupRequest = RemoveRoleFromGroupRequest("group-id", "role-id")
        val updatedAvroGroup = AvroGroup().apply {
            id = "group-id"
            name = "Test Group"
            roleAssociations = emptyList()
        }

        whenever(
            groupWriter.removeRoleFromGroup(
                removeRoleFromGroupRequest,
                creatorUserId
            )
        ).thenReturn(updatedAvroGroup)

        val future = CompletableFuture<PermissionManagementResponse>()
        processor.onNext(
            request = PermissionManagementRequest().apply {
                request = removeRoleFromGroupRequest
                requestUserId = creatorUserId
            },
            respFuture = future
        )

        verify(permissionStorageReader, times(1)).publishUpdatedGroup(updatedAvroGroup)
        verify(permissionStorageReader, times(1)).reconcilePermissionSummaries()

        val response = future.getOrThrow().response
        assertTrue(response is AvroGroup)
        (response as? AvroGroup)?.let { group ->
            assertEquals(updatedAvroGroup, group)
            assertTrue(group.roleAssociations.isEmpty())
        }
    }

    @Test
    fun `receiving DeleteGroupRequest calls group writer and publishes deleted group`() {
        val deleteGroupRequest = DeleteGroupRequest("group-id")
        val deletedAvroGroup = AvroGroup().apply {
            id = "group-id"
            name = "Deleted Group"
        }

        whenever(groupWriter.deleteGroup(deleteGroupRequest, creatorUserId)).thenReturn(deletedAvroGroup)

        val future = CompletableFuture<PermissionManagementResponse>()
        processor.onNext(
            request = PermissionManagementRequest().apply {
                request = deleteGroupRequest
                requestUserId = creatorUserId
            },
            respFuture = future
        )

        verify(permissionStorageReader, times(1)).publishDeletedGroup(deletedAvroGroup.id)

        val response = future.getOrThrow().response
        assertTrue(response is AvroGroup)
        (response as? AvroGroup)?.let { group ->
            assertEquals(deletedAvroGroup, group)
            assertEquals(group.id, deleteGroupRequest.groupId)
        }
    }

    @Test
    fun `DeleteGroupRequest receives exception and completes future with exception in response`() {
        val deleteGroupRequest = DeleteGroupRequest("group-id")
        whenever(groupWriter.deleteGroup(deleteGroupRequest, creatorUserId))
            .thenThrow(IllegalArgumentException("Group deletion error."))

        val future = CompletableFuture<PermissionManagementResponse>()
        processor.onNext(
            request = PermissionManagementRequest().apply {
                request = deleteGroupRequest
                requestUserId = creatorUserId
            },
            respFuture = future
        )

        verify(permissionStorageReader, never()).publishDeletedGroup(any())

        val result = future.getOrThrow()
        assertTrue(result.response is ExceptionEnvelope)
        val exception = result.response as ExceptionEnvelope
        assertEquals(IllegalArgumentException::class.java.name, exception.errorType)
        assertEquals("Group deletion error.", exception.errorMessage)
    }
}
