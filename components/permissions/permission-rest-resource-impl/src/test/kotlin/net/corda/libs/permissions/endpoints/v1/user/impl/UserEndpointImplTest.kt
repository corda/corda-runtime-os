package net.corda.libs.permissions.endpoints.v1.user.impl

import java.lang.IllegalArgumentException
import net.corda.libs.permissions.endpoints.v1.user.types.CreateUserType
import net.corda.httprpc.exception.ResourceNotFoundException
import net.corda.httprpc.security.CURRENT_REST_CONTEXT
import net.corda.httprpc.security.RestAuthContext
import net.corda.libs.permissions.manager.PermissionManager
import net.corda.libs.permissions.manager.request.CreateUserRequestDto
import net.corda.libs.permissions.manager.request.GetUserRequestDto
import net.corda.libs.permissions.manager.response.UserResponseDto
import net.corda.lifecycle.LifecycleCoordinator
import net.corda.lifecycle.LifecycleCoordinatorFactory
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
import net.corda.httprpc.exception.InternalServerException
import net.corda.httprpc.exception.InvalidInputDataException
import net.corda.libs.permissions.manager.request.AddRoleToUserRequestDto
import net.corda.libs.permissions.manager.request.RemoveRoleFromUserRequestDto
import net.corda.libs.permissions.manager.response.RoleAssociationResponseDto
import net.corda.libs.permissions.manager.response.RoleResponseDto
import org.junit.jupiter.api.Assertions.assertTrue
import java.util.UUID
import net.corda.permissions.management.PermissionManagementService
import org.assertj.core.api.Assertions

internal class UserEndpointImplTest {

    private val now = Instant.now()
    private val parentGroup = UUID.randomUUID().toString()

    private val createUserType = CreateUserType(
        "fullName1",
        "loginName1",
        true,
        "initialPass",
        now,
        parentGroup
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
        parentGroup,
        emptyList(),
        emptyList(),
    )

    private val roleResponseDto = RoleResponseDto("roleId1", 1, Instant.now(), "Role Name", null, emptyList())

    private val lifecycleCoordinator: LifecycleCoordinator = mock()
    private val lifecycleCoordinatorFactory = mock<LifecycleCoordinatorFactory>().also {
        whenever(it.createCoordinator(any(), any())).thenReturn(lifecycleCoordinator)
    }
    private val permissionManager = mock<PermissionManager>()
    private val permissionService = mock<PermissionManagementService>().also {
        whenever(it.permissionManager).thenReturn(permissionManager)
    }

    private val endpoint = UserEndpointImpl(lifecycleCoordinatorFactory, permissionService)

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
    fun `create a user successfully`() {
        val createUserDtoCapture = argumentCaptor<CreateUserRequestDto>()
        whenever(lifecycleCoordinator.isRunning).thenReturn(true)
        whenever(permissionService.isRunning).thenReturn(true)
        whenever(permissionManager.createUser(createUserDtoCapture.capture())).thenReturn(userResponseDto)

        endpoint.start()
        val response = endpoint.createUser(createUserType)
        val responseType = response.responseBody

        assertEquals(ResponseCode.CREATED, response.responseCode)
        assertNotNull(responseType)
        assertEquals("uuid", responseType.id)
        assertEquals(0, responseType.version)
        assertEquals(now, responseType.updateTimestamp)
        assertEquals("fullName1", responseType.fullName)
        assertEquals("loginName1", responseType.loginName)
        assertEquals(true, responseType.enabled)
        assertEquals(now, responseType.passwordExpiry)
        assertEquals(parentGroup, responseType.parentGroup)
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
        assertEquals(parentGroup, responseType.parentGroup)
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
        assertEquals("User 'abc' not found.", e.message)
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
            parentGroup,
            emptyList(),
            listOf(RoleAssociationResponseDto("roleId1", Instant.now()))
        )

        val capture = argumentCaptor<AddRoleToUserRequestDto>()
        whenever(lifecycleCoordinator.isRunning).thenReturn(true)
        whenever(permissionService.isRunning).thenReturn(true)
        whenever(permissionManager.addRoleToUser(capture.capture())).thenReturn(userResponseDtoWithRole)

        endpoint.start()
        val response = endpoint.addRole("userLogin1", "roleId1")
        val responseType = response.responseBody

        assertEquals(ResponseCode.OK, response.responseCode)

        assertEquals(1, capture.allValues.size)
        assertEquals("aRestUser", capture.firstValue.requestedBy)
        assertEquals("userlogin1", capture.firstValue.loginName)
        assertEquals("roleId1", capture.firstValue.roleId)

        assertNotNull(responseType)
        assertEquals("uuid", responseType.id)
        assertEquals(0, responseType.version)
        assertEquals(now, responseType.updateTimestamp)
        assertEquals("fullName1", responseType.fullName)
        assertEquals("loginName1", responseType.loginName)
        assertEquals(true, responseType.enabled)
        assertEquals(now, responseType.passwordExpiry)
        assertEquals(parentGroup, responseType.parentGroup)

        assertEquals(1, responseType.roleAssociations.size)
        assertEquals("roleId1", responseType.roleAssociations.first().roleId)
    }

    @Test
    fun `add role to user responds with exception when an exception from permission manager`() {
        whenever(lifecycleCoordinator.isRunning).thenReturn(true)
        whenever(permissionService.isRunning).thenReturn(true)
        whenever(permissionManager.addRoleToUser(any())).thenThrow(IllegalArgumentException("Exc"))

        endpoint.start()
        val e = assertThrows<InternalServerException> {
            endpoint.addRole("userLogin1", "roleId1")
        }
        assertEquals("Unexpected permission management error occurred.", e.message)
    }

    @Test
    fun `remove role from user`() {
        val capture = argumentCaptor<RemoveRoleFromUserRequestDto>()
        whenever(lifecycleCoordinator.isRunning).thenReturn(true)
        whenever(permissionService.isRunning).thenReturn(true)
        whenever(permissionManager.removeRoleFromUser(capture.capture())).thenReturn(userResponseDto)
        whenever(permissionManager.getRole(any())).thenReturn(roleResponseDto)
        whenever(permissionManager.getUser(any())).thenReturn(userResponseDto)

        endpoint.start()
        val response = endpoint.removeRole("userLogin1", "roleId1")
        val responseType = response.responseBody

        assertEquals(ResponseCode.OK, response.responseCode)

        assertEquals(1, capture.allValues.size)
        assertEquals("aRestUser", capture.firstValue.requestedBy)
        assertEquals("userlogin1", capture.firstValue.loginName)
        assertEquals("roleId1", capture.firstValue.roleId)

        assertNotNull(responseType)
        assertEquals("uuid", responseType.id)
        assertEquals(0, responseType.version)
        assertEquals(now, responseType.updateTimestamp)
        assertEquals("fullName1", responseType.fullName)
        assertEquals("loginName1", responseType.loginName)
        assertEquals(true, responseType.enabled)
        assertEquals(now, responseType.passwordExpiry)
        assertEquals(parentGroup, responseType.parentGroup)
        assertTrue(responseType.roleAssociations.isEmpty())
    }

    @Test
    fun `remove role from user responds with exception when an exception from permission manager`() {
        whenever(lifecycleCoordinator.isRunning).thenReturn(true)
        whenever(permissionService.isRunning).thenReturn(true)
        whenever(permissionManager.removeRoleFromUser(any())).thenThrow(IllegalArgumentException("Exc"))
        whenever(permissionManager.getRole(any())).thenReturn(roleResponseDto)
        whenever(permissionManager.getUser(any())).thenReturn(userResponseDto)

        endpoint.start()
        val e = assertThrows<InternalServerException> {
            endpoint.removeRole("userLogin1", "roleId1")
        }
        assertEquals("Unexpected permission management error occurred.", e.message)
    }

    @Test
    fun `create with invalid user login`() {
        whenever(lifecycleCoordinator.isRunning).thenReturn(true)
        whenever(permissionService.isRunning).thenReturn(true)

        endpoint.start()
        Assertions.assertThatThrownBy {
            endpoint.createUser(createUserType.copy(loginName = "foo/bar"))
        }.isInstanceOf(InvalidInputDataException::class.java)
            .hasMessageContaining("Invalid input data for user creation.")
    }
}