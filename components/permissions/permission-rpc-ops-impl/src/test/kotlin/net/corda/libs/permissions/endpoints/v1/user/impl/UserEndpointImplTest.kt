package net.corda.libs.permissions.endpoints.v1.user.impl

import net.corda.httprpc.exception.HttpApiException
import net.corda.httprpc.exception.ResourceNotFoundException
import net.corda.httprpc.security.CURRENT_RPC_CONTEXT
import net.corda.httprpc.security.RpcAuthContext
import net.corda.libs.permissions.endpoints.v1.user.types.CreateUserType
import net.corda.libs.permissions.manager.PermissionManager
import net.corda.libs.permissions.manager.request.CreateUserRequestDto
import net.corda.libs.permissions.manager.request.GetUserRequestDto
import net.corda.libs.permissions.manager.response.UserResponseDto
import net.corda.lifecycle.LifecycleCoordinator
import net.corda.lifecycle.LifecycleCoordinatorFactory
import net.corda.permissions.service.PermissionServiceComponent
import net.corda.v5.base.util.Try
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
        whenever(permissionManager.createUser(createUserDtoCapture.capture())).thenReturn(mockTry)
        whenever(mockTry.getOrThrow()).thenReturn(userResponseDto)

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
    fun `create a user throws with status 500 when permission manager is not running`() {
        endpoint.start()
        whenever(permissionManager.isRunning).thenReturn(false)

        val e = assertThrows<HttpApiException> {
            endpoint.createUser(createUserType)
        }
        assertEquals(500, e.statusCode)
    }

    @Test
    fun `create a user throws with status 500 when this service is not running`() {
        val e = assertThrows<HttpApiException> {
            endpoint.createUser(createUserType)
        }
        assertEquals(500, e.statusCode)
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
    fun `get a user throws with status 500 when permission manager is not running`() {
        endpoint.start()
        whenever(permissionManager.isRunning).thenReturn(false)

        val e = assertThrows<HttpApiException> {
            endpoint.getUser("abc")
        }
        assertEquals(500, e.statusCode)
    }

    @Test
    fun `get a user throws with status 500 when this service is not running`() {
        val e = assertThrows<HttpApiException> {
            endpoint.getUser("abc")
        }
        assertEquals(500, e.statusCode)
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
        assertEquals(null, e.statusCode, "Resource not found exception should not override any status codes.")
        assertEquals("User abc not found.", e.message)
        assertEquals("abc", getUserRequestDtoCapture.firstValue.loginName)
    }
}