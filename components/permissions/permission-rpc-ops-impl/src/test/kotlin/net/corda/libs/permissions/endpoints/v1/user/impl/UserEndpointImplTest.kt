package net.corda.libs.permissions.endpoints.v1.user.impl

import java.lang.IllegalArgumentException
import net.corda.libs.permissions.endpoints.v1.user.types.CreateUserType
import net.corda.httprpc.exception.ResourceNotFoundException
import net.corda.httprpc.security.CURRENT_RPC_CONTEXT
import net.corda.httprpc.security.RpcAuthContext
import net.corda.libs.permissions.manager.PermissionManager
import net.corda.libs.permissions.manager.request.CreateUserRequestDto
import net.corda.libs.permissions.manager.request.GetUserRequestDto
import net.corda.libs.permissions.manager.response.UserResponseDto
import net.corda.lifecycle.LifecycleCoordinator
import net.corda.lifecycle.LifecycleCoordinatorFactory
import net.corda.permissions.service.PermissionServiceComponent
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import java.time.Instant
import net.corda.httprpc.ResponseCode
import net.corda.libs.permissions.manager.request.AddRoleToUserRequestDto
import net.corda.libs.permissions.manager.request.RemoveRoleFromUserRequestDto
import net.corda.libs.permissions.manager.response.RoleAssociationResponseDto
import org.junit.jupiter.api.Assertions.assertTrue

internal class UserEndpointImplTest {

    private val now = Instant.now()
    private val createUserType = CreateUserType(
        "fullName1",
        "loginName1",
        true,
        "initialPass",
        now,
        "parentGroupId"
    )

    private val userResponseDto = UserResponseDto(
        "uuid",
        0,
        now,
        "fullName1",
        "loginName1",
        true,
        false,
        now,
        "parentGroupId",
        emptyList(),
        emptyList(),
    )

    private val lifecycleCoordinator: LifecycleCoordinator = mock<LifecycleCoordinator>()
    private val lifecycleCoordinatorFactory = mock<LifecycleCoordinatorFactory>().also {
        whenever(it.createCoordinator(any(), any())).thenReturn(lifecycleCoordinator)
    }
    private val permissionManager = mock<PermissionManager>()
    private val permissionService = mock<PermissionServiceComponent>().also {
        whenever(it.permissionManager).thenReturn(permissionManager)
    }

    private val endpoint = UserEndpointImpl(lifecycleCoordinatorFactory, permissionService)

    @BeforeEach
    fun beforeEach() {
        val authContext = mock<RpcAuthContext>().apply {
            whenever(principal).thenReturn("anRpcUser")
        }
        CURRENT_RPC_CONTEXT.set(authContext)
    }

    @Test
    fun getProtocolVersion() {
        assertEquals(1, endpoint.protocolVersion)
    }

    @Test
    fun `create a user successfully`() {
        val createUserDtoCapture = argumentCaptor<CreateUserRequestDto>()
        whenever(lifecycleCoordinator.isRunning).thenReturn(true)
        whenever(permissionService.isRunning).thenReturn(true)
        whenever(permissionManager.createUser(createUserDtoCapture.capture())).thenReturn(userResponseDto)

        endpoint.start()
        val responseType = endpoint.createUser(createUserType)

        assertEquals("uuid", responseType.id)
        assertEquals(0, responseType.version)
        assertEquals(now, responseType.updateTimestamp)
        assertEquals("fullName1", responseType.fullName)
        assertEquals("loginName1", responseType.loginName)
        assertEquals(true, responseType.enabled)
        assertEquals(now, responseType.passwordExpiry)
        assertEquals("parentGroupId", responseType.parentGroup)
    }

    @Test
    fun `get a user successfully`() {
        val getUserRequestDtoCapture = argumentCaptor<GetUserRequestDto>()
        whenever(lifecycleCoordinator.isRunning).thenReturn(true)
        whenever(permissionService.isRunning).thenReturn(true)
        whenever(permissionManager.getUser(getUserRequestDtoCapture.capture())).thenReturn(userResponseDto)

        endpoint.start()
        val responseType = endpoint.getUser("loginName1")

        assertNotNull(responseType)
        assertEquals("uuid", responseType.id)
        assertEquals(0, responseType.version)
        assertEquals(now, responseType.updateTimestamp)
        assertEquals("fullName1", responseType.fullName)
        assertEquals("loginName1", responseType.loginName)
        assertEquals(true, responseType.enabled)
        assertEquals(now, responseType.passwordExpiry)
        assertEquals("parentGroupId", responseType.parentGroup)
    }

    @Test
    fun `get a user throws with resource not found exception when the user isn't found`() {
        val getUserRequestDtoCapture = argumentCaptor<GetUserRequestDto>()
        whenever(permissionManager.getUser(getUserRequestDtoCapture.capture())).thenReturn(null)
        whenever(lifecycleCoordinator.isRunning).thenReturn(true)
        whenever(permissionService.isRunning).thenReturn(true)

        val e = assertThrows<ResourceNotFoundException> {
            endpoint.getUser("abc")
        }
        assertEquals(ResponseCode.RESOURCE_NOT_FOUND, e.responseCode, "Resource not found exception should have correct response code.")
        assertEquals("User abc not found.", e.message)
        assertEquals("abc", getUserRequestDtoCapture.firstValue.loginName)
    }

    @Test
    fun `add role to user`() {
        val userResponseDtoWithRole = UserResponseDto(
            "uuid",
            0,
            now,
            "fullName1",
            "loginName1",
            true,
            false,
            now,
            "parentGroupId",
            emptyList(),
            listOf(RoleAssociationResponseDto("roleId1", Instant.now()))
        )

        val capture = argumentCaptor<AddRoleToUserRequestDto>()
        whenever(lifecycleCoordinator.isRunning).thenReturn(true)
        whenever(permissionService.isRunning).thenReturn(true)
        whenever(permissionManager.addRoleToUser(capture.capture())).thenReturn(userResponseDtoWithRole)

        endpoint.start()
        val responseType = endpoint.addRole("userLogin1", "roleId1")

        assertEquals(1, capture.allValues.size)
        assertEquals("anRpcUser", capture.firstValue.requestedBy)
        assertEquals("userLogin1", capture.firstValue.loginName)
        assertEquals("roleId1", capture.firstValue.roleId)

        assertNotNull(responseType)
        assertEquals("uuid", responseType.id)
        assertEquals(0, responseType.version)
        assertEquals(now, responseType.updateTimestamp)
        assertEquals("fullName1", responseType.fullName)
        assertEquals("loginName1", responseType.loginName)
        assertEquals(true, responseType.enabled)
        assertEquals(now, responseType.passwordExpiry)
        assertEquals("parentGroupId", responseType.parentGroup)

        assertEquals(1, responseType.roleAssociations.size)
        assertEquals("roleId1", responseType.roleAssociations.first().roleId)
    }

    @Test
    fun `add role to user responds with exception when an exception from permission manager`() {
        whenever(lifecycleCoordinator.isRunning).thenReturn(true)
        whenever(permissionService.isRunning).thenReturn(true)
        whenever(permissionManager.addRoleToUser(any())).thenThrow(IllegalArgumentException("Exc"))

        endpoint.start()
        val e = assertThrows<IllegalArgumentException> {
            endpoint.addRole("userLogin1", "roleId1")
        }
        assertEquals("Exc", e.message)
    }

    @Test
    fun `remove role from user`() {
        val capture = argumentCaptor<RemoveRoleFromUserRequestDto>()
        whenever(lifecycleCoordinator.isRunning).thenReturn(true)
        whenever(permissionService.isRunning).thenReturn(true)
        whenever(permissionManager.removeRoleFromUser(capture.capture())).thenReturn(userResponseDto)

        endpoint.start()
        val responseType = endpoint.removeRole("userLogin1", "roleId1")

        assertEquals(1, capture.allValues.size)
        assertEquals("anRpcUser", capture.firstValue.requestedBy)
        assertEquals("userLogin1", capture.firstValue.loginName)
        assertEquals("roleId1", capture.firstValue.roleId)

        assertNotNull(responseType)
        assertEquals("uuid", responseType.id)
        assertEquals(0, responseType.version)
        assertEquals(now, responseType.updateTimestamp)
        assertEquals("fullName1", responseType.fullName)
        assertEquals("loginName1", responseType.loginName)
        assertEquals(true, responseType.enabled)
        assertEquals(now, responseType.passwordExpiry)
        assertEquals("parentGroupId", responseType.parentGroup)
        assertTrue(responseType.roleAssociations.isEmpty())
    }

    @Test
    fun `remove role from user responds with exception when an exception from permission manager`() {
        whenever(lifecycleCoordinator.isRunning).thenReturn(true)
        whenever(permissionService.isRunning).thenReturn(true)
        whenever(permissionManager.removeRoleFromUser(any())).thenThrow(IllegalArgumentException("Exc"))

        endpoint.start()
        val e = assertThrows<IllegalArgumentException> {
            endpoint.removeRole("userLogin1", "roleId1")
        }
        assertEquals("Exc", e.message)
    }
}