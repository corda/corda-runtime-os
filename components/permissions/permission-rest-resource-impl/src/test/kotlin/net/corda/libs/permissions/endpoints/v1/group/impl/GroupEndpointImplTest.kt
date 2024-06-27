package net.corda.libs.permissions.endpoints.v1.group.impl

import net.corda.libs.permissions.endpoints.v1.group.types.CreateGroupType
import net.corda.libs.permissions.manager.PermissionManager
import net.corda.libs.permissions.manager.response.GroupContentResponseDto
import net.corda.libs.permissions.manager.response.GroupResponseDto
import net.corda.libs.platform.PlatformInfoProvider
import net.corda.permissions.management.PermissionManagementService
import net.corda.rest.ResponseCode
import net.corda.rest.security.CURRENT_REST_CONTEXT
import net.corda.rest.security.RestAuthContext
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.*
import java.time.Instant
import java.util.UUID

internal class GroupEndpointImplTest {

    private val now = Instant.now()
    private val parentGroup = UUID.randomUUID().toString()

    private val createGroupType = CreateGroupType(
        "groupName1",
        parentGroup
    )

    private val groupResponseDto = GroupResponseDto(
        "uuid",
        now,
        "groupName1",
        parentGroup,
        emptyList(),
        emptyList(),
    )

    private val groupContentResponseDto = GroupContentResponseDto(
        "uuid",
        now,
        "groupName1",
        parentGroup,
        emptyList(),
        emptyList(),
        emptyList(),
        emptyList()
    )

    private val permissionManager = mock<PermissionManager>()
    private val permissionService = mock<PermissionManagementService>().also {
        whenever(it.permissionManager).thenReturn(permissionManager)
    }

    private val platformInfoProvider = mock<PlatformInfoProvider>().also {
        whenever(it.localWorkerPlatformVersion).thenReturn(1)
    }

    private val endpoint = GroupEndpointImpl(permissionService, platformInfoProvider)

    @BeforeEach
    fun beforeEach() {
        whenever(permissionManager.createGroup(any())).thenReturn(groupResponseDto)
        whenever(permissionManager.changeParentGroup(any())).thenReturn(groupResponseDto)
        whenever(permissionManager.addRoleToGroup(any())).thenReturn(groupResponseDto)
        whenever(permissionManager.removeRoleFromGroup(any())).thenReturn(groupResponseDto)
        whenever(permissionManager.deleteGroup(any())).thenReturn(groupResponseDto)
        whenever(permissionManager.getGroupContent(any())).thenReturn(groupContentResponseDto)

        val authContext = mock<RestAuthContext>().apply {
            whenever(principal).thenReturn("aRestUser")
        }
        CURRENT_REST_CONTEXT.set(authContext)
    }

    @Test
    fun `createGroup calls PermissionGroupManager with correct parameters`() {
        val response = endpoint.createGroup(createGroupType)
        val responseType = response.responseBody

        assertEquals(ResponseCode.CREATED, response.responseCode)
        assertNotNull(responseType)
        assertEquals("uuid", responseType.id)
        assertEquals(now, responseType.updateTimestamp)
        assertEquals("groupName1", responseType.name)
        assertEquals(parentGroup, responseType.parentGroupId)
    }

    @Test
    fun `get a group successfully`() {
        val groupId = "uuid"
        val responseType = endpoint.getGroupContent(groupId)

        assertNotNull(responseType)
        assertEquals("uuid", responseType.id)
        assertEquals(now, responseType.updateTimestamp)
        assertEquals("groupName1", responseType.name)
        assertEquals(parentGroup, responseType.parentGroupId)
    }

    @Test
    fun `add role to group`() {
        val groupId = "uuid"
        val roleId = "roleId1"
        val response = endpoint.addRole(groupId, roleId)
        val responseType = response.responseBody

        assertEquals(ResponseCode.OK, response.responseCode)
        assertNotNull(responseType)
        assertEquals("uuid", responseType.id)
        assertEquals(now, responseType.updateTimestamp)
        assertEquals("groupName1", responseType.name)
        assertEquals(parentGroup, responseType.parentGroupId)
    }

    @Test
    fun `remove role from group`() {
        val groupId = "uuid"
        val roleId = "roleId1"
        val response = endpoint.removeRole(groupId, roleId)
        val responseType = response.responseBody

        assertEquals(ResponseCode.OK, response.responseCode)
        assertNotNull(responseType)
        assertEquals("uuid", responseType.id)
        assertEquals(now, responseType.updateTimestamp)
        assertEquals("groupName1", responseType.name)
        assertEquals(parentGroup, responseType.parentGroupId)
    }

    @Test
    fun `delete a group`() {
        val groupId = "uuid"
        val response = endpoint.deleteGroup(groupId)
        val responseType = response.responseBody

        assertEquals(ResponseCode.OK, response.responseCode)
        assertNotNull(responseType)
        assertEquals("uuid", responseType.id)
        assertEquals(now, responseType.updateTimestamp)
        assertEquals("groupName1", responseType.name)
        assertEquals(parentGroup, responseType.parentGroupId)
    }
}