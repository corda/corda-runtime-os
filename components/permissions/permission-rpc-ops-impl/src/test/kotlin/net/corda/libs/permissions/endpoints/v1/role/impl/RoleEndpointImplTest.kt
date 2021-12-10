package net.corda.libs.permissions.endpoints.v1.role.impl

import java.time.Instant
import net.corda.httprpc.exception.HttpApiException
import net.corda.httprpc.exception.ResourceNotFoundException
import net.corda.libs.permissions.endpoints.v1.role.types.CreateRoleRequestType
import net.corda.libs.permissions.manager.PermissionManager
import net.corda.libs.permissions.manager.request.CreateRoleRequestDto
import net.corda.libs.permissions.manager.request.GetRoleRequestDto
import net.corda.libs.permissions.manager.response.RoleResponseDto
import net.corda.lifecycle.LifecycleCoordinator
import net.corda.lifecycle.LifecycleCoordinatorFactory
import net.corda.permissions.service.PermissionServiceComponent
import net.corda.v5.base.util.Try
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

internal class RoleEndpointImplTest {

    private val now = Instant.now()
    private val createRoleRequestType = CreateRoleRequestType("roleName", null)

    private val mockTry = mock<Try<RoleResponseDto>>()
    private val roleResponseDto = RoleResponseDto("uuid", 0, now, "roleName", null, emptyList())

    private val lifecycleCoordinator: LifecycleCoordinator = mock()
    private val lifecycleCoordinatorFactory = mock<LifecycleCoordinatorFactory>().also {
        whenever(it.createCoordinator(any(), any())).thenReturn(lifecycleCoordinator)
    }
    private val permissionManager = mock<PermissionManager>()
    private val permissionService = mock<PermissionServiceComponent>().also {
        whenever(it.permissionManager).thenReturn(permissionManager)
    }

    private val endpoint = RoleEndpointImpl(lifecycleCoordinatorFactory, permissionService)

    @Test
    fun getProtocolVersion() {
        assertEquals(1, endpoint.protocolVersion)
    }

    @Test
    fun `create a role successfully`() {
        val createRoleDtoCapture = argumentCaptor<CreateRoleRequestDto>()
        whenever(lifecycleCoordinator.isRunning).thenReturn(true)
        whenever(permissionService.isRunning).thenReturn(true)
        whenever(permissionManager.createRole(createRoleDtoCapture.capture())).thenReturn(mockTry)
        whenever(mockTry.getOrThrow()).thenReturn(roleResponseDto)

        endpoint.start()
        val responseType = endpoint.createRole(createRoleRequestType)

        assertEquals("uuid", responseType.id)
        assertEquals(0, responseType.version)
        assertEquals(now, responseType.updateTimestamp)
        assertEquals("roleName", responseType.roleName)
    }

    @Test
    fun `create a role throws with status 500 when permission manager is not running`() {
        endpoint.start()
        whenever(permissionManager.isRunning).thenReturn(false)

        val e = assertThrows<HttpApiException> {
            endpoint.createRole(createRoleRequestType)
        }
        assertEquals(500, e.statusCode)
    }

    @Test
    fun `create a role throws with status 500 when this service is not running`() {
        val e = assertThrows<HttpApiException> {
            endpoint.createRole(createRoleRequestType)
        }
        assertEquals(500, e.statusCode)
    }

    @Test
    fun `get a role successfully`() {
        val getRoleRequestDtoCapture = argumentCaptor<GetRoleRequestDto>()
        whenever(lifecycleCoordinator.isRunning).thenReturn(true)
        whenever(permissionService.isRunning).thenReturn(true)
        whenever(permissionManager.getRole(getRoleRequestDtoCapture.capture())).thenReturn(roleResponseDto)

        endpoint.start()
        val responseType = endpoint.getRole("roleName")

        Assertions.assertNotNull(responseType)
        assertEquals("uuid", responseType.id)
        assertEquals(0, responseType.version)
        assertEquals(now, responseType.updateTimestamp)
        assertEquals("roleName", responseType.roleName)
    }

    @Test
    fun `get a role throws with status 500 when permission manager is not running`() {
        endpoint.start()
        whenever(permissionManager.isRunning).thenReturn(false)

        val e = assertThrows<HttpApiException> {
            endpoint.getRole("abc")
        }
        assertEquals(500, e.statusCode)
    }

    @Test
    fun `get a role throws with status 500 when this service is not running`() {
        val e = assertThrows<HttpApiException> {
            endpoint.getRole("abc")
        }
        assertEquals(500, e.statusCode)
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
        assertEquals(null, e.statusCode, "Resource not found exception should not override any status codes.")
        assertEquals("Role abc not found.", e.message)
        assertEquals("abc", getRoleRequestDtoCapture.firstValue.roleName)
    }
}