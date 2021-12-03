package net.corda.libs.permissions.endpoints.v1.user.impl

import net.corda.libs.permissions.endpoints.exception.PermissionEndpointException
import net.corda.libs.permissions.endpoints.v1.user.types.CreateUserType
import net.corda.libs.permissions.manager.PermissionManager
import net.corda.libs.permissions.manager.request.CreateUserRequestDto
import net.corda.libs.permissions.manager.request.GetUserRequestDto
import net.corda.libs.permissions.manager.response.UserResponseDto
import net.corda.lifecycle.LifecycleCoordinator
import net.corda.lifecycle.LifecycleCoordinatorFactory
import net.corda.permissions.service.PermissionServiceComponent
import net.corda.v5.base.util.Try
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import java.time.Instant

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

    private val mockTry = mock<Try<UserResponseDto>>()
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
        emptyList()
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

    @Test
    fun getProtocolVersion() {
        Assertions.assertEquals(1, endpoint.protocolVersion)
    }

    @Test
    fun `create a user successfully`() {
        val createUserDtoCapture = argumentCaptor<CreateUserRequestDto>()
        whenever(lifecycleCoordinator.isRunning).thenReturn(true)
        whenever(permissionService.isRunning).thenReturn(true)
        whenever(permissionManager.createUser(createUserDtoCapture.capture())).thenReturn(mockTry)
        whenever(mockTry.getOrThrow()).thenReturn(userResponseDto)

        endpoint.start()
        val responseType = endpoint.createUser(createUserType)

        Assertions.assertEquals("uuid", responseType.id)
        Assertions.assertEquals(0, responseType.version)
        Assertions.assertEquals(now, responseType.updateTimestamp)
        Assertions.assertEquals("fullName1", responseType.fullName)
        Assertions.assertEquals("loginName1", responseType.loginName)
        Assertions.assertEquals(true, responseType.enabled)
        Assertions.assertEquals(now, responseType.passwordExpiry)
        Assertions.assertEquals("parentGroupId", responseType.parentGroup)
    }

    @Test
    fun `create a user throws with status 500 when permission manager is not running`() {
        endpoint.start()
        whenever(permissionManager.isRunning).thenReturn(false)

        val e = assertThrows<PermissionEndpointException> {
            endpoint.createUser(createUserType)
        }
        Assertions.assertEquals(500, e.status)
    }

    @Test
    fun `create a user throws with status 500 when this service is not running`() {
        val e = assertThrows<PermissionEndpointException> {
            endpoint.createUser(createUserType)
        }
        Assertions.assertEquals(500, e.status)
    }

    @Test
    fun `get a user successfully`() {
        val getUserRequestDtoCapture = argumentCaptor<GetUserRequestDto>()
        whenever(lifecycleCoordinator.isRunning).thenReturn(true)
        whenever(permissionService.isRunning).thenReturn(true)
        whenever(permissionManager.getUser(getUserRequestDtoCapture.capture())).thenReturn(userResponseDto)

        endpoint.start()
        val responseType = endpoint.getUser("loginName1")

        Assertions.assertNotNull(responseType)
        Assertions.assertEquals("uuid", responseType!!.id)
        Assertions.assertEquals(0, responseType.version)
        Assertions.assertEquals(now, responseType.updateTimestamp)
        Assertions.assertEquals("fullName1", responseType.fullName)
        Assertions.assertEquals("loginName1", responseType.loginName)
        Assertions.assertEquals(true, responseType.enabled)
        Assertions.assertEquals(now, responseType.passwordExpiry)
        Assertions.assertEquals("parentGroupId", responseType.parentGroup)
    }

    @Test
    fun `get a user throws with status 500 when permission manager is not running`() {
        endpoint.start()
        whenever(permissionManager.isRunning).thenReturn(false)

        val e = assertThrows<PermissionEndpointException> {
            endpoint.getUser("abc")
        }
        Assertions.assertEquals(500, e.status)
    }

    @Test
    fun `get a user throws with status 500 when this service is not running`() {
        val e = assertThrows<PermissionEndpointException> {
            endpoint.getUser("abc")
        }
        Assertions.assertEquals(500, e.status)
    }
}