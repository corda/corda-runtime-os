package net.corda.libs.permissions.endpoints.v1.role.impl

import net.corda.rest.exception.ResourceNotFoundException
import net.corda.rest.security.CURRENT_REST_CONTEXT
import net.corda.rest.security.RestAuthContext
import net.corda.libs.permissions.endpoints.v1.role.types.CreateRoleType
import net.corda.libs.permissions.manager.PermissionManager
import net.corda.libs.permissions.manager.request.CreateRoleRequestDto
import net.corda.libs.permissions.manager.request.GetRoleRequestDto
import net.corda.libs.permissions.manager.response.RoleResponseDto
import net.corda.lifecycle.LifecycleCoordinator
import net.corda.lifecycle.LifecycleCoordinatorFactory
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import java.time.Instant
import net.corda.rest.ResponseCode
import net.corda.permissions.management.PermissionManagementService
import org.junit.jupiter.api.Assertions.assertNotNull

internal class RoleEndpointImplTest {

    private val now = Instant.now()
    private val createRoleType = CreateRoleType("roleName", null)

    private val roleResponseDto = RoleResponseDto("roleId", 0, now, "roleName", null, emptyList())

    private val lifecycleCoordinator: LifecycleCoordinator = mock()
    private val lifecycleCoordinatorFactory = mock<LifecycleCoordinatorFactory>().also {
        whenever(it.createCoordinator(any(), any())).thenReturn(lifecycleCoordinator)
    }
    private val permissionManager = mock<PermissionManager>()
    private val permissionService = mock<PermissionManagementService>().also {
        whenever(it.permissionManager).thenReturn(permissionManager)
    }

    private val endpoint = RoleEndpointImpl(lifecycleCoordinatorFactory, permissionService)

    @BeforeEach
    fun beforeEach() {
        val authContext = mock<RestAuthContext>().apply {
            whenever(principal).thenReturn("aRestUser")
        }
        CURRENT_REST_CONTEXT.set(authContext)
    }

    @Test
    fun getProtocolVersion() {
        assertEquals(1, endpoint.protocolVersion)
    }

    @Test
    fun `create a role successfully`() {
        val createRoleDtoCapture = argumentCaptor<CreateRoleRequestDto>()
        whenever(lifecycleCoordinator.isRunning).thenReturn(true)
        whenever(permissionService.isRunning).thenReturn(true)
        whenever(permissionManager.createRole(createRoleDtoCapture.capture())).thenReturn(roleResponseDto)

        endpoint.start()
        val response = endpoint.createRole(createRoleType)
        val responseType = response.responseBody

        assertEquals(ResponseCode.CREATED, response.responseCode)
        assertNotNull(responseType)
        assertEquals("roleId", responseType.id)
        assertEquals(0, responseType.version)
        assertEquals(now, responseType.updateTimestamp)
        assertEquals("roleName", responseType.roleName)
    }

    @Test
    fun `get a role successfully`() {
        val getRoleRequestDtoCapture = argumentCaptor<GetRoleRequestDto>()
        whenever(lifecycleCoordinator.isRunning).thenReturn(true)
        whenever(permissionService.isRunning).thenReturn(true)
        whenever(permissionManager.getRole(getRoleRequestDtoCapture.capture())).thenReturn(roleResponseDto)

        endpoint.start()
        val responseType = endpoint.getRole("roleId")

        assertNotNull(responseType)
        assertEquals("roleId", responseType.id)
        assertEquals(0, responseType.version)
        assertEquals(now, responseType.updateTimestamp)
        assertEquals("roleName", responseType.roleName)
    }

    @Test
    fun `get a role throws with resource not found exception when the role isn't found`() {
        val getRoleRequestDtoCapture = argumentCaptor<GetRoleRequestDto>()
        whenever(permissionManager.getRole(getRoleRequestDtoCapture.capture())).thenReturn(null)
        whenever(lifecycleCoordinator.isRunning).thenReturn(true)
        whenever(permissionService.isRunning).thenReturn(true)

        val e = assertThrows<ResourceNotFoundException> {
            endpoint.getRole("abc")
        }
        assertEquals(ResponseCode.RESOURCE_NOT_FOUND, e.responseCode, "Resource not found exception should have appropriate response code.")
        assertEquals("Role 'abc' not found.", e.message)
        assertEquals("abc", getRoleRequestDtoCapture.firstValue.roleId)
    }
}