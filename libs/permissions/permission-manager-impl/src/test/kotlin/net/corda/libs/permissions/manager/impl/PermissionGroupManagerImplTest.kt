package net.corda.libs.permissions.manager.impl

import net.corda.data.permissions.ChangeDetails
import net.corda.data.permissions.Group
import net.corda.data.permissions.management.PermissionManagementRequest
import net.corda.data.permissions.management.PermissionManagementResponse
import net.corda.data.permissions.management.group.AddRoleToGroupRequest
import net.corda.data.permissions.management.group.ChangeGroupParentIdRequest
import net.corda.data.permissions.management.group.CreateGroupRequest
import net.corda.data.permissions.management.group.DeleteGroupRequest
import net.corda.data.permissions.management.group.RemoveRoleFromGroupRequest
import net.corda.libs.configuration.SmartConfig
import net.corda.libs.permissions.management.cache.PermissionManagementCache
import net.corda.libs.permissions.manager.exception.UnexpectedPermissionResponseException
import net.corda.libs.permissions.manager.request.AddRoleToGroupRequestDto
import net.corda.libs.permissions.manager.request.ChangeGroupParentIdDto
import net.corda.libs.permissions.manager.request.CreateGroupRequestDto
import net.corda.libs.permissions.manager.request.DeleteGroupRequestDto
import net.corda.libs.permissions.manager.request.RemoveRoleFromGroupRequestDto
import net.corda.messaging.api.publisher.RPCSender
import net.corda.utilities.concurrent.getOrThrow
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import java.time.Duration
import java.time.Instant
import java.util.*
import java.util.concurrent.CompletableFuture
import java.util.concurrent.atomic.AtomicReference

class PermissionGroupManagerImplTest {

    private val permissionManagementCache = mock<PermissionManagementCache>()
    private val permissionManagementCacheRef = AtomicReference(permissionManagementCache)

    private lateinit var rpcSender: RPCSender<PermissionManagementRequest, PermissionManagementResponse>
    private lateinit var restConfig: SmartConfig
    private lateinit var manager: PermissionGroupManagerImpl
    private val defaultTimeout = Duration.ofSeconds(30)

    @BeforeEach
    fun setup() {
        rpcSender = mock()
        restConfig = mock()

        manager = PermissionGroupManagerImpl(
            restConfig,
            rpcSender,
            permissionManagementCacheRef
        )
    }

    @Test
    fun `create a group sends rpc request and converts result`() {
        val groupId = UUID.randomUUID().toString()
        val avroGroup = Group(groupId, 0, ChangeDetails(Instant.now()), "groupName", "parentGroupId", emptyList(), emptyList())

        val future = mock<CompletableFuture<PermissionManagementResponse>>()
        whenever(future.getOrThrow(defaultTimeout)).thenReturn(PermissionManagementResponse(avroGroup))

        val requestCaptor = argumentCaptor<PermissionManagementRequest>()
        whenever(rpcSender.sendRequest(requestCaptor.capture())).thenReturn(future)

        val createGroupRequestDto = CreateGroupRequestDto("requestedBy", "groupName", "parentGroupId")
        val result = manager.createGroup(createGroupRequestDto)

        val capturedPermissionManagementRequest = requestCaptor.firstValue
        assertEquals("requestedBy", capturedPermissionManagementRequest.requestUserId)
        assertEquals("cluster", capturedPermissionManagementRequest.virtualNodeId)

        val capturedCreateGroupRequest = capturedPermissionManagementRequest.request as CreateGroupRequest
        assertEquals("groupName", capturedCreateGroupRequest.groupName)
        assertEquals("parentGroupId", capturedCreateGroupRequest.parentGroupId)

        assertEquals(groupId, result.id)
        assertEquals("groupName", result.groupName)
        assertEquals("parentGroupId", result.parentGroupId)
    }

    @Test
    fun `create a group throws exception if result is not an avro Group`() {
        val future = mock<CompletableFuture<PermissionManagementResponse>>()
        whenever(future.getOrThrow(defaultTimeout)).thenReturn(PermissionManagementResponse(true))

        val requestCaptor = argumentCaptor<PermissionManagementRequest>()
        whenever(rpcSender.sendRequest(requestCaptor.capture())).thenReturn(future)

        val createGroupRequestDto = CreateGroupRequestDto("requestedBy", "groupName", "parentGroupId")

        assertThrows<UnexpectedPermissionResponseException> {
            manager.createGroup(createGroupRequestDto)
        }
    }

    @Test
    fun `change parent group sends rpc request and converts result to response dto`() {
        val groupId = UUID.randomUUID().toString()
        val newParentGroupId = UUID.randomUUID().toString()
        val avroGroup = Group(groupId, 0, ChangeDetails(Instant.now()), "groupName", newParentGroupId, emptyList(), emptyList())

        val future = mock<CompletableFuture<PermissionManagementResponse>>()
        whenever(future.getOrThrow(defaultTimeout)).thenReturn(PermissionManagementResponse(avroGroup))

        val requestCaptor = argumentCaptor<PermissionManagementRequest>()
        whenever(rpcSender.sendRequest(requestCaptor.capture())).thenReturn(future)

        val changeGroupParentIdDto = ChangeGroupParentIdDto("requestedBy", groupId, newParentGroupId)
        val result = manager.changeParentGroup(changeGroupParentIdDto)

        val capturedPermissionManagementRequest = requestCaptor.firstValue
        assertEquals("requestedBy", capturedPermissionManagementRequest.requestUserId)
        assertNull(capturedPermissionManagementRequest.virtualNodeId)

        val capturedChangeGroupParentIdRequest = capturedPermissionManagementRequest.request as ChangeGroupParentIdRequest
        assertEquals(groupId, capturedChangeGroupParentIdRequest.groupId)
        assertEquals(newParentGroupId, capturedChangeGroupParentIdRequest.newParentGroupId)

        assertEquals(groupId, result.id)
        assertEquals("groupName", result.groupName)
        assertEquals(newParentGroupId, result.parentGroupId)
        assertTrue(result.properties.isEmpty())
        assertTrue(result.roleAssociations.isEmpty())
    }

    @Test
    fun `add role to group sends rpc request and converts result to response dto`() {
        val groupId = UUID.randomUUID().toString()
        val avroGroup = Group(groupId, 0, ChangeDetails(Instant.now()), "groupName", "parentGroupId", emptyList(), emptyList())

        val future = mock<CompletableFuture<PermissionManagementResponse>>()
        whenever(future.getOrThrow(defaultTimeout)).thenReturn(PermissionManagementResponse(avroGroup))

        val requestCaptor = argumentCaptor<PermissionManagementRequest>()
        whenever(rpcSender.sendRequest(requestCaptor.capture())).thenReturn(future)

        val addRoleToGroupRequestDto = AddRoleToGroupRequestDto("requestedBy", groupId, "roleId")
        val result = manager.addRoleToGroup(addRoleToGroupRequestDto)

        val capturedPermissionManagementRequest = requestCaptor.firstValue
        assertEquals("requestedBy", capturedPermissionManagementRequest.requestUserId)
        assertNull(capturedPermissionManagementRequest.virtualNodeId)

        val capturedAddRoleToGroupRequest = capturedPermissionManagementRequest.request as AddRoleToGroupRequest
        assertEquals(groupId, capturedAddRoleToGroupRequest.groupId)
        assertEquals("roleId", capturedAddRoleToGroupRequest.roleId)

        assertEquals(groupId, result.id)
        assertEquals("groupName", result.groupName)
        assertEquals("parentGroupId", result.parentGroupId)
        assertTrue(result.properties.isEmpty())
        assertTrue(result.roleAssociations.isEmpty())
    }

    @Test
    fun `add role to group throws if exception is returned`() {
        val future = mock<CompletableFuture<PermissionManagementResponse>>()
        whenever(future.getOrThrow(defaultTimeout)).thenThrow(IllegalArgumentException("Invalid group."))

        val requestCaptor = argumentCaptor<PermissionManagementRequest>()
        whenever(rpcSender.sendRequest(requestCaptor.capture())).thenReturn(future)

        val addRoleToGroupRequestDto = AddRoleToGroupRequestDto("requestedBy", "invalid-group-id", "roleId")

        val e = assertThrows<IllegalArgumentException> {
            manager.addRoleToGroup(addRoleToGroupRequestDto)
        }

        assertEquals("Invalid group.", e.message)
    }

    @Test
    fun `get a group uses the cache and converts avro group to dto`() {
        val groupId = UUID.randomUUID().toString()
        val avroGroup = Group(groupId, 0, ChangeDetails(Instant.now()), "groupName", "parentGroupId", emptyList(), emptyList())

        whenever(permissionManagementCache.getGroup(groupId)).thenReturn(avroGroup)

        val result = manager.getGroupContent(groupId)

        assertNotNull(result)
        assertEquals(groupId, result!!.id)
        assertEquals("groupName", result.groupName)
        assertEquals("parentGroupId", result.parentGroupId)
        assertTrue(result.properties.isEmpty())
        assertTrue(result.roleAssociations.isEmpty())
    }

    @Test
    fun `get a group returns null when group doesn't exist in cache`() {
        val groupId = "invalid-group-id"

        whenever(permissionManagementCache.getGroup(groupId)).thenReturn(null)

        val result = manager.getGroupContent(groupId)

        assertNull(result)
    }

    @Test
    fun `remove role from group sends rpc request and converts result to response dto`() {
        val groupId = UUID.randomUUID().toString()
        val avroGroup = Group(groupId, 0, ChangeDetails(Instant.now()), "groupName", "parentGroupId", emptyList(), emptyList())

        val future = mock<CompletableFuture<PermissionManagementResponse>>()
        whenever(future.getOrThrow(defaultTimeout)).thenReturn(PermissionManagementResponse(avroGroup))

        val requestCaptor = argumentCaptor<PermissionManagementRequest>()
        whenever(rpcSender.sendRequest(requestCaptor.capture())).thenReturn(future)

        val removeRoleFromGroupRequestDto = RemoveRoleFromGroupRequestDto("requestedBy", groupId, "roleId")
        val result = manager.removeRoleFromGroup(removeRoleFromGroupRequestDto)

        val capturedPermissionManagementRequest = requestCaptor.firstValue
        assertEquals("requestedBy", capturedPermissionManagementRequest.requestUserId)
        assertNull(capturedPermissionManagementRequest.virtualNodeId)

        val capturedRemoveRoleFromGroupRequest = capturedPermissionManagementRequest.request as RemoveRoleFromGroupRequest
        assertEquals(groupId, capturedRemoveRoleFromGroupRequest.groupId)
        assertEquals("roleId", capturedRemoveRoleFromGroupRequest.roleId)

        assertEquals(groupId, result.id)
        assertEquals("groupName", result.groupName)
        assertEquals("parentGroupId", result.parentGroupId)
        assertTrue(result.properties.isEmpty())
        assertTrue(result.roleAssociations.isEmpty())
    }

    @Test
    fun `delete a group sends rpc request and converts result`() {
        val groupId = UUID.randomUUID().toString()
        val avroGroup = Group(groupId, 0, ChangeDetails(Instant.now()), "groupName", "parentGroupId", emptyList(), emptyList())

        val future = mock<CompletableFuture<PermissionManagementResponse>>()
        whenever(future.getOrThrow(defaultTimeout)).thenReturn(PermissionManagementResponse(avroGroup))

        val requestCaptor = argumentCaptor<PermissionManagementRequest>()
        whenever(rpcSender.sendRequest(requestCaptor.capture())).thenReturn(future)

        val deleteGroupRequestDto = DeleteGroupRequestDto("requestedBy", groupId)
        val result = manager.deleteGroup(deleteGroupRequestDto)

        val capturedPermissionManagementRequest = requestCaptor.firstValue
        assertEquals("requestedBy", capturedPermissionManagementRequest.requestUserId)
        assertNull(capturedPermissionManagementRequest.virtualNodeId)

        val capturedDeleteGroupRequest = capturedPermissionManagementRequest.request as DeleteGroupRequest
        assertEquals(groupId, capturedDeleteGroupRequest.groupId)

        assertEquals(groupId, result.id)
        assertEquals("groupName", result.groupName)
        assertEquals("parentGroupId", result.parentGroupId)
        assertTrue(result.properties.isEmpty())
        assertTrue(result.roleAssociations.isEmpty())
    }
}
