package net.corda.libs.permissions.endpoints.v1.group.impl

import net.corda.libs.permissions.endpoints.v1.group.types.CreateGroupType
import net.corda.libs.permissions.manager.PermissionManager
import net.corda.libs.permissions.manager.request.AddRoleToGroupRequestDto
import net.corda.libs.permissions.manager.request.CreateGroupRequestDto
import net.corda.libs.permissions.manager.request.DeleteGroupRequestDto
import net.corda.libs.permissions.manager.request.RemoveRoleFromGroupRequestDto
import net.corda.libs.permissions.manager.response.GroupContentResponseDto
import net.corda.libs.permissions.manager.response.GroupResponseDto
import net.corda.libs.platform.PlatformInfoProvider
import net.corda.lifecycle.LifecycleCoordinatorFactory
import net.corda.permissions.management.PermissionManagementService
import net.corda.rest.ResponseCode
import net.corda.rest.security.CURRENT_REST_CONTEXT
import net.corda.rest.security.RestAuthContext
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
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
        0,
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

    private val lifecycleCoordinatorFactory = mock<LifecycleCoordinatorFactory>()

    private val endpoint = GroupEndpointImpl(lifecycleCoordinatorFactory, permissionService, platformInfoProvider)

    @BeforeEach
    fun beforeEach() {
        val authContext = mock<RestAuthContext>().apply {
            whenever(principal).thenReturn("aRestUser")
        }
        CURRENT_REST_CONTEXT.set(authContext)
    }

    @Test
    fun `create a group successfully`() {
        val createGroupDtoCapture = argumentCaptor<CreateGroupRequestDto>()
        whenever(permissionManager.createGroup(createGroupDtoCapture.capture())).thenReturn(groupResponseDto)

        val response = endpoint.createGroup(createGroupType)
        val responseType = response.responseBody

        assertEquals(ResponseCode.CREATED, response.responseCode)
        assertNotNull(responseType)
        assertEquals("uuid", responseType.id)
        assertEquals(now, responseType.updateTimestamp)
        assertEquals("groupName1", responseType.name)
        assertEquals(parentGroup, responseType.parentGroupId)

        val capturedDto = createGroupDtoCapture.firstValue
        assertEquals("groupName1", capturedDto.groupName)
        assertEquals(parentGroup, capturedDto.parentGroupId)
    }

    @Test
    fun `get a group successfully`() {
        whenever(permissionManager.getGroupContent(any())).thenReturn(groupContentResponseDto)

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
        val addRoleDtoCapture = argumentCaptor<AddRoleToGroupRequestDto>()
        whenever(permissionManager.addRoleToGroup(addRoleDtoCapture.capture())).thenReturn(groupResponseDto)

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

        val capturedDto = addRoleDtoCapture.firstValue
        assertEquals(groupId, capturedDto.groupId)
        assertEquals(roleId, capturedDto.roleId)
    }

    @Test
    fun `remove role from group`() {
        val removeRoleDtoCapture = argumentCaptor<RemoveRoleFromGroupRequestDto>()
        whenever(permissionManager.removeRoleFromGroup(removeRoleDtoCapture.capture())).thenReturn(groupResponseDto)

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

        val capturedDto = removeRoleDtoCapture.firstValue
        assertEquals(groupId, capturedDto.groupId)
        assertEquals(roleId, capturedDto.roleId)
    }

    @Test
    fun `delete a group`() {
        val deleteGroupDtoCapture = argumentCaptor<DeleteGroupRequestDto>()
        whenever(permissionManager.deleteGroup(deleteGroupDtoCapture.capture())).thenReturn(groupResponseDto)

        val groupId = "uuid"
        val response = endpoint.deleteGroup(groupId)
        val responseType = response.responseBody

        assertEquals(ResponseCode.OK, response.responseCode)
        assertNotNull(responseType)
        assertEquals("uuid", responseType.id)
        assertEquals(now, responseType.updateTimestamp)
        assertEquals("groupName1", responseType.name)
        assertEquals(parentGroup, responseType.parentGroupId)

        val capturedDto = deleteGroupDtoCapture.firstValue
        assertEquals(groupId, capturedDto.groupId)
    }
}
