package net.corda.libs.permissions.manager.impl

import com.typesafe.config.ConfigValueFactory
import net.corda.data.permissions.ChangeDetails
import net.corda.data.permissions.Property
import net.corda.data.permissions.RoleAssociation
import net.corda.data.permissions.User
import net.corda.data.permissions.management.PermissionManagementRequest
import net.corda.data.permissions.management.PermissionManagementResponse
import net.corda.data.permissions.management.user.AddPropertyToUserRequest
import net.corda.data.permissions.management.user.AddRoleToUserRequest
import net.corda.data.permissions.management.user.CreateUserRequest
import net.corda.data.permissions.management.user.DeleteUserRequest
import net.corda.data.permissions.management.user.RemovePropertyFromUserRequest
import net.corda.data.permissions.management.user.RemoveRoleFromUserRequest
import net.corda.libs.configuration.SmartConfig
import net.corda.libs.configuration.SmartConfigImpl
import net.corda.libs.permissions.management.cache.PermissionManagementCache
import net.corda.libs.permissions.manager.exception.UnexpectedPermissionResponseException
import net.corda.libs.permissions.manager.request.AddPropertyToUserRequestDto
import net.corda.libs.permissions.manager.request.AddRoleToUserRequestDto
import net.corda.libs.permissions.manager.request.ChangeUserPasswordDto
import net.corda.libs.permissions.manager.request.CreateUserRequestDto
import net.corda.libs.permissions.manager.request.DeleteUserRequestDto
import net.corda.libs.permissions.manager.request.GetUserPropertiesRequestDto
import net.corda.libs.permissions.manager.request.GetUserRequestDto
import net.corda.libs.permissions.manager.request.GetUsersByPropertyRequestDto
import net.corda.libs.permissions.manager.request.RemovePropertyFromUserRequestDto
import net.corda.libs.permissions.manager.request.RemoveRoleFromUserRequestDto
import net.corda.libs.permissions.manager.response.PropertyResponseDto
import net.corda.libs.permissions.manager.response.UserResponseDto
import net.corda.libs.permissions.validation.cache.PermissionValidationCache
import net.corda.messaging.api.publisher.RPCSender
import net.corda.permissions.password.PasswordHash
import net.corda.permissions.password.PasswordService
import net.corda.schema.configuration.ConfigKeys
import net.corda.utilities.concurrent.getOrThrow
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.lang.IllegalArgumentException
import java.time.Duration
import java.time.Instant
import java.util.UUID
import java.util.concurrent.CompletableFuture
import java.util.concurrent.atomic.AtomicReference

class PermissionUserManagerImplTest {

    private val permissionManagementCache = mock<PermissionManagementCache>()
    private val permissionManagementCacheRef = AtomicReference(permissionManagementCache)
    private val permissionValidationCache = mock<PermissionValidationCache>()
    private val passwordService = mock<PasswordService>()

    private val fullName = "first last"
    private val requestUserName = "requestUserName"
    private val passwordExpiry = Instant.now()
    private val parentGroup = "some-parent-group"

    private val createUserRequestDto = CreateUserRequestDto(
        requestedBy = requestUserName,
        loginName = "loginname123",
        fullName = fullName,
        enabled = true,
        initialPassword = "mypassword",
        passwordExpiry = passwordExpiry,
        parentGroup = parentGroup
    )
    private val createUserRequestDtoWithoutPassword = CreateUserRequestDto(
        requestedBy = requestUserName,
        loginName = "loginname123",
        fullName = fullName,
        enabled = true,
        initialPassword = null,
        passwordExpiry = null,
        parentGroup = parentGroup
    )

    private val deleteUserRequestDto = DeleteUserRequestDto(requestedBy = requestUserName, loginName = "loginname123")
    private val userCreationTime = Instant.now()
    private val getUserRequestDto = GetUserRequestDto(requestedBy = requestUserName, loginName = "loginname123")
    private val getUserPropertiesRequestDto =
        GetUserPropertiesRequestDto(requestedBy = requestUserName, loginName = "loginname123")
    private val getUsersByPropertyRequestDto = GetUsersByPropertyRequestDto(
        requestedBy = requestUserName,
        propertyKey = "email",
        propertyValue = "a@b.com"
    )
    private val changeUserPasswordDto = ChangeUserPasswordDto("requestedBy", "loginname123", "mypassword")
    private val userProperty = Property(
        UUID.randomUUID().toString(),
        0,
        ChangeDetails(userCreationTime),
        "email",
        "a@b.com"
    )

    private val avroUser = User(
        UUID.randomUUID().toString(), 0, ChangeDetails(userCreationTime), "user-login1", fullName, true,
        "temp-hashed-password", "temporary-salt", userCreationTime, false, parentGroup, listOf(userProperty),
        listOf(RoleAssociation(ChangeDetails(userCreationTime), "roleId1"))
    )

    private val avroUserWithoutPassword = User(
        UUID.randomUUID().toString(), 0, ChangeDetails(userCreationTime),
        "user-login2", fullName, true, null, null, null,
        true, parentGroup, listOf(userProperty), listOf(RoleAssociation(ChangeDetails(userCreationTime), "roleId1"))
    )

    private val permissionManagementResponse = PermissionManagementResponse(avroUser)
    private val permissionManagementResponseWithoutPassword = PermissionManagementResponse(avroUserWithoutPassword)

    private lateinit var rpcSender: RPCSender<PermissionManagementRequest, PermissionManagementResponse>
    private var restConfig = mock<SmartConfig>()
    private lateinit var rbacConfig: SmartConfig
    private lateinit var manager: PermissionUserManagerImpl

    @BeforeEach
    fun setup() {
        rpcSender = mock()
        rbacConfig = mock()
        whenever(rbacConfig.getInt(ConfigKeys.RBAC_USER_PASSWORD_CHANGE_EXPIRY)).thenReturn(30)
        whenever(rbacConfig.getInt(ConfigKeys.RBAC_ADMIN_PASSWORD_CHANGE_EXPIRY)).thenReturn(7)
        whenever(rbacConfig.getInt(ConfigKeys.RBAC_PASSWORD_LENGTH_LIMIT)).thenReturn(100)

        manager = PermissionUserManagerImpl(
            restConfig, rbacConfig, rpcSender, permissionManagementCacheRef,
            AtomicReference(permissionValidationCache), passwordService
        )
    }

    private val defaultTimeout = Duration.ofSeconds(30)

    @Test
    fun `create a user sends rpc request and converts result`() {
        val future = mock<CompletableFuture<PermissionManagementResponse>>()
        whenever(future.getOrThrow(defaultTimeout)).thenReturn(permissionManagementResponse)

        val requestCaptor = argumentCaptor<PermissionManagementRequest>()
        whenever(rpcSender.sendRequest(requestCaptor.capture())).thenReturn(future)

        whenever(passwordService.saltAndHash(eq("mypassword"))).thenReturn(PasswordHash("randomSalt", "hashedPass"))

        val result = manager.createUser(createUserRequestDto)

        val capturedPermissionManagementRequest = requestCaptor.firstValue
        assertEquals(requestUserName, capturedPermissionManagementRequest.requestUserId)
        assertEquals("cluster", capturedPermissionManagementRequest.virtualNodeId)

        val capturedCreateUserRequest = capturedPermissionManagementRequest.request as CreateUserRequest
        assertEquals(createUserRequestDto.loginName, capturedCreateUserRequest.loginName)
        assertEquals(createUserRequestDto.fullName, capturedCreateUserRequest.fullName)
        assertEquals(createUserRequestDto.enabled, capturedCreateUserRequest.enabled)
        assertEquals("hashedPass", capturedCreateUserRequest.initialHashedPassword)
        assertEquals("randomSalt", capturedCreateUserRequest.saltValue)
        assertNotNull(capturedCreateUserRequest.passwordExpiry)
        assertEquals(
            createUserRequestDto.passwordExpiry!!.toEpochMilli(),
            capturedCreateUserRequest.passwordExpiry.toEpochMilli()
        )
        assertEquals(createUserRequestDto.parentGroup, capturedCreateUserRequest.parentGroupId)

        assertEquals(fullName, result.fullName)
        assertEquals(avroUser.enabled, result.enabled)
        assertEquals(avroUser.lastChangeDetails.updateTimestamp, result.lastUpdatedTimestamp)
        assertEquals(false, result.ssoAuth)
        assertEquals(avroUser.parentGroupId, result.parentGroup)
        assertEquals(1, result.properties.size)

        val property = result.properties.first()
        assertEquals(userProperty.lastChangeDetails.updateTimestamp, property.lastChangedTimestamp)
        assertEquals(userProperty.key, property.key)
        assertEquals(userProperty.value, property.value)
    }

    @Test
    fun `create a user sends rpc request and converts result correctly when no password is provided`() {
        val future = mock<CompletableFuture<PermissionManagementResponse>>()
        whenever(future.getOrThrow(defaultTimeout)).thenReturn(permissionManagementResponseWithoutPassword)

        val requestCaptor = argumentCaptor<PermissionManagementRequest>()
        whenever(rpcSender.sendRequest(requestCaptor.capture())).thenReturn(future)

        val result = manager.createUser(createUserRequestDtoWithoutPassword)

        verify(passwordService, times(0)).saltAndHash(any())

        val capturedPermissionManagementRequest = requestCaptor.firstValue
        assertEquals(requestUserName, capturedPermissionManagementRequest.requestUserId)
        assertEquals("cluster", capturedPermissionManagementRequest.virtualNodeId)

        val capturedCreateUserRequest = capturedPermissionManagementRequest.request as CreateUserRequest
        assertEquals(createUserRequestDto.loginName, capturedCreateUserRequest.loginName)
        assertEquals(createUserRequestDto.fullName, capturedCreateUserRequest.fullName)
        assertEquals(createUserRequestDto.enabled, capturedCreateUserRequest.enabled)
        assertNull(capturedCreateUserRequest.initialHashedPassword)
        assertNull(capturedCreateUserRequest.saltValue)
        assertNull(capturedCreateUserRequest.passwordExpiry)
        assertEquals(createUserRequestDto.parentGroup, capturedCreateUserRequest.parentGroupId)

        assertEquals(fullName, result.fullName)
        assertEquals(avroUser.enabled, result.enabled)
        assertEquals(avroUser.lastChangeDetails.updateTimestamp, result.lastUpdatedTimestamp)
        assertEquals(true, result.ssoAuth)
        assertEquals(avroUser.parentGroupId, result.parentGroup)
        assertEquals(1, result.properties.size)

        val property = result.properties.first()
        assertEquals(userProperty.lastChangeDetails.updateTimestamp, property.lastChangedTimestamp)
        assertEquals(userProperty.key, property.key)
        assertEquals(userProperty.value, property.value)
    }

    @Test
    fun `create a user throws exception if result is not an avro User`() {
        val future = mock<CompletableFuture<PermissionManagementResponse>>()
        val incorrectResponse = PermissionManagementResponse(true)
        whenever(future.getOrThrow(defaultTimeout)).thenReturn(incorrectResponse)

        val requestCaptor = argumentCaptor<PermissionManagementRequest>()
        whenever(rpcSender.sendRequest(requestCaptor.capture())).thenReturn(future)

        assertThrows(UnexpectedPermissionResponseException::class.java) { manager.createUser(createUserRequestDto) }
    }

    @Test
    fun `delete a user sends rpc request and converts result`() {
        val future = mock<CompletableFuture<PermissionManagementResponse>>()
        whenever(future.getOrThrow(defaultTimeout)).thenReturn(permissionManagementResponse)

        val requestCaptor = argumentCaptor<PermissionManagementRequest>()
        whenever(rpcSender.sendRequest(requestCaptor.capture())).thenReturn(future)

        val result = manager.deleteUser(deleteUserRequestDto)

        val capturedPermissionManagementRequest = requestCaptor.firstValue
        assertEquals(requestUserName, capturedPermissionManagementRequest.requestUserId)
        assertEquals(null, capturedPermissionManagementRequest.virtualNodeId)

        val capturedCreateUserRequest = capturedPermissionManagementRequest.request as DeleteUserRequest
        assertEquals(deleteUserRequestDto.loginName, capturedCreateUserRequest.loginName)

        assertEquals(fullName, result.fullName)
        assertEquals(avroUser.enabled, result.enabled)
        assertEquals(avroUser.lastChangeDetails.updateTimestamp, result.lastUpdatedTimestamp)
        assertEquals(false, result.ssoAuth)
        assertEquals(avroUser.parentGroupId, result.parentGroup)
        assertEquals(1, result.properties.size)

        val property = result.properties.first()
        assertEquals(userProperty.lastChangeDetails.updateTimestamp, property.lastChangedTimestamp)
        assertEquals(userProperty.key, property.key)
        assertEquals(userProperty.value, property.value)
    }

    @Test
    fun `get a user uses the cache and converts avro user to dto`() {
        whenever(permissionManagementCache.getUser("loginname123")).thenReturn(avroUser)

        val result = manager.getUser(getUserRequestDto)

        assertNotNull(result)
        assertEquals(fullName, result!!.fullName)
        assertEquals(avroUser.enabled, result.enabled)
        assertEquals(avroUser.lastChangeDetails.updateTimestamp, result.lastUpdatedTimestamp)
        assertEquals(avroUser.ssoAuth, result.ssoAuth)
        assertEquals(avroUser.parentGroupId, result.parentGroup)
        assertEquals(1, result.properties.size)

        val property = result.properties.first()
        assertEquals(userProperty.lastChangeDetails.updateTimestamp, property.lastChangedTimestamp)
        assertEquals(userProperty.key, property.key)
        assertEquals(userProperty.value, property.value)
    }

    @Test
    fun `get a user returns null when user doesn't exist in cache`() {
        whenever(permissionManagementCache.getUser("invalid-user-login-name")).thenReturn(null)

        val result = manager.getUser(getUserRequestDto)

        assertNull(result)
    }

    @Test
    fun `changeUserPasswordSelf fails if the new password is the same as the existing one`() {
        whenever(permissionManagementCache.getUser("loginname123")).thenReturn(avroUser)
        whenever(passwordService.verifies(eq("mypassword"), any())).thenReturn(true)

        val exception = assertThrows<IllegalArgumentException> {
            manager.changeUserPasswordSelf(changeUserPasswordDto)
        }

        assertEquals("New password must be different from the current one.", exception.message)
    }

    @Test
    fun `changeUserPasswordOther fails if the new password is the same as the existing one`() {
        whenever(permissionManagementCache.getUser("loginname123")).thenReturn(avroUser)
        whenever(passwordService.verifies(eq("mypassword"), any())).thenReturn(true)

        val exception = assertThrows<IllegalArgumentException> {
            manager.changeUserPasswordOther(changeUserPasswordDto)
        }

        assertEquals("New password must be different from the current one.", exception.message)
    }

    @Test
    fun `changeUserPasswordOther fails if the new password is too long`() {
        whenever(permissionManagementCache.getUser("loginname123")).thenReturn(avroUser)

        val veryLongString = "abc".repeat(1000)
        val exception = assertThrows<IllegalArgumentException> {
            manager.changeUserPasswordOther(changeUserPasswordDto.copy(newPassword = veryLongString))
        }

        assertEquals("Password exceed current length limit of 100.", exception.message)
    }

    @Test
    fun `creating permission user manager will use the remote writer timeout set in the config`() {
        val config = SmartConfigImpl.empty()
            .withValue(ConfigKeys.REST_ENDPOINT_TIMEOUT_MILLIS, ConfigValueFactory.fromAnyRef(12345L))
            .withValue(ConfigKeys.RBAC_USER_PASSWORD_CHANGE_EXPIRY, ConfigValueFactory.fromAnyRef(30))
            .withValue(ConfigKeys.RBAC_ADMIN_PASSWORD_CHANGE_EXPIRY, ConfigValueFactory.fromAnyRef(7))

        val future = mock<CompletableFuture<PermissionManagementResponse>>()
        val requestCaptor = argumentCaptor<PermissionManagementRequest>()
        whenever(rpcSender.sendRequest(requestCaptor.capture())).thenReturn(future)
        whenever(passwordService.saltAndHash(eq("mypassword"))).thenReturn(PasswordHash("randomSalt", "hashedPass"))
        whenever(future.getOrThrow(Duration.ofMillis(12345L))).thenReturn(permissionManagementResponse)

        val manager = PermissionUserManagerImpl(
            config,
            rbacConfig,
            rpcSender,
            permissionManagementCacheRef,
            AtomicReference(permissionValidationCache),
            passwordService
        )

        val result = manager.createUser(createUserRequestDto)

        verify(future, times(1)).getOrThrow(Duration.ofMillis(12345L))

        assertEquals(avroUser.id, result.id)
    }

    @Test
    fun `add role to user sends rpc request and converts result to response dto`() {
        val future = mock<CompletableFuture<PermissionManagementResponse>>()
        whenever(future.getOrThrow(defaultTimeout)).thenReturn(permissionManagementResponse)

        val capture = argumentCaptor<PermissionManagementRequest>()
        whenever(rpcSender.sendRequest(capture.capture())).thenReturn(future)

        val requestDto = AddRoleToUserRequestDto("requestUserId", "user-login1", "roleId1")
        val result = manager.addRoleToUser(requestDto)

        assertEquals("requestUserId", capture.firstValue.requestUserId)
        assertNull(capture.firstValue.virtualNodeId)

        val capturedRequest = capture.firstValue.request as AddRoleToUserRequest
        assertEquals("user-login1", capturedRequest.loginName)
        assertEquals("roleId1", capturedRequest.roleId)

        assertEquals("user-login1", result.loginName)
        assertEquals(1, result.roles.size)
        assertEquals("roleId1", result.roles[0].roleId)
    }

    @Test
    fun `add role to user throws if exception is returned`() {
        val future = mock<CompletableFuture<PermissionManagementResponse>>()
        whenever(future.getOrThrow(defaultTimeout)).thenThrow(IllegalArgumentException("Invalid user."))

        val capture = argumentCaptor<PermissionManagementRequest>()
        whenever(rpcSender.sendRequest(capture.capture())).thenReturn(future)

        val requestDto = AddRoleToUserRequestDto("requestUserId", "user-login1", "roleId1")

        val e = assertThrows<IllegalArgumentException> {
            manager.addRoleToUser(requestDto)
        }

        assertEquals("Invalid user.", e.message)
    }

    @Test
    fun `remove role from user sends rpc request and converts result to response dto`() {
        val avroUser = User(
            UUID.randomUUID().toString(), 0, ChangeDetails(userCreationTime), "user-login1", fullName, true,
            "temp-hashed-password", "temporary-salt", userCreationTime, false, parentGroup, listOf(userProperty),
            emptyList()
        )
        val permissionManagementResponse = PermissionManagementResponse(avroUser)

        val future = mock<CompletableFuture<PermissionManagementResponse>>()
        whenever(future.getOrThrow(defaultTimeout)).thenReturn(permissionManagementResponse)

        val capture = argumentCaptor<PermissionManagementRequest>()
        whenever(rpcSender.sendRequest(capture.capture())).thenReturn(future)

        val requestDto = RemoveRoleFromUserRequestDto("requestUserId", "user-login1", "roleId1")
        val result = manager.removeRoleFromUser(requestDto)

        assertEquals("requestUserId", capture.firstValue.requestUserId)
        assertNull(capture.firstValue.virtualNodeId)

        val capturedRequest = capture.firstValue.request as RemoveRoleFromUserRequest
        assertEquals("user-login1", capturedRequest.loginName)
        assertEquals("roleId1", capturedRequest.roleId)

        assertEquals("user-login1", result.loginName)
        assertEquals(0, result.roles.size)
    }

    @Test
    fun `remove role from user throws if exception is returned`() {
        val future = mock<CompletableFuture<PermissionManagementResponse>>()
        whenever(future.getOrThrow(defaultTimeout)).thenThrow(IllegalArgumentException("Invalid user."))

        val capture = argumentCaptor<PermissionManagementRequest>()
        whenever(rpcSender.sendRequest(capture.capture())).thenReturn(future)

        val requestDto = RemoveRoleFromUserRequestDto("requestUserId", "user-login1", "roleId1")

        val e = assertThrows<IllegalArgumentException> {
            manager.removeRoleFromUser(requestDto)
        }

        assertEquals("Invalid user.", e.message)
    }

    @Test
    fun `add property to user sends rpc request`() {
        val future = mock<CompletableFuture<PermissionManagementResponse>>()
        whenever(future.getOrThrow(defaultTimeout)).thenReturn(permissionManagementResponse)

        val capture = argumentCaptor<PermissionManagementRequest>()
        whenever(rpcSender.sendRequest(capture.capture())).thenReturn(future)

        val requestDto = AddPropertyToUserRequestDto("requestUserId", "user-login1", mapOf("email" to "a@b.com"))
        val result = manager.addPropertyToUser(requestDto)
        assertEquals("requestUserId", capture.firstValue.requestUserId)
        assertNull(capture.firstValue.virtualNodeId)

        val capturedRequest = capture.firstValue.request as AddPropertyToUserRequest
        assertEquals("user-login1", capturedRequest.loginName)
        assertEquals(mapOf("email" to "a@b.com"), capturedRequest.properties)

        assertEquals("user-login1", result.loginName)
        assertEquals(1, result.properties.size)
        assertEquals("email", result.properties.first().key)
        assertEquals("a@b.com", result.properties.first().value)
    }

    @Test
    fun `add property to user throws if exception is returned`() {
        val future = mock<CompletableFuture<PermissionManagementResponse>>()
        whenever(future.getOrThrow(defaultTimeout)).thenThrow(IllegalArgumentException("Invalid user."))

        val capture = argumentCaptor<PermissionManagementRequest>()
        whenever(rpcSender.sendRequest(capture.capture())).thenReturn(future)

        val requestDto = AddPropertyToUserRequestDto("requestUserId", "user-login1", mapOf("email" to "a@b.com"))

        val e = assertThrows<IllegalArgumentException> {
            manager.addPropertyToUser(requestDto)
        }

        assertEquals("Invalid user.", e.message)
    }

    @Test
    fun `remove property from user sends rpc request and converts result to response dto`() {
        val avroUser = User(
            UUID.randomUUID().toString(), 0, ChangeDetails(userCreationTime), "user-login1", fullName, true,
            "temp-hashed-password", "temporary-salt", userCreationTime, false, parentGroup, emptyList(),
            emptyList()
        )
        val permissionManagementResponse = PermissionManagementResponse(avroUser)

        val future = mock<CompletableFuture<PermissionManagementResponse>>()
        whenever(future.getOrThrow(defaultTimeout)).thenReturn(permissionManagementResponse)

        val capture = argumentCaptor<PermissionManagementRequest>()
        whenever(rpcSender.sendRequest(capture.capture())).thenReturn(future)

        val requestDto = RemovePropertyFromUserRequestDto("requestUserId", "user-login1", "email")
        val result = manager.removePropertyFromUser(requestDto)

        assertEquals("requestUserId", capture.firstValue.requestUserId)
        assertNull(capture.firstValue.virtualNodeId)

        val capturedRequest = capture.firstValue.request as RemovePropertyFromUserRequest
        assertEquals("user-login1", capturedRequest.loginName)
        assertEquals("email", capturedRequest.propertyKey)
        assertEquals("user-login1", result.loginName)
        assertEquals(0, result.properties.size)
    }

    @Test
    fun `remove property from user throws if exception is returned`() {
        val future = mock<CompletableFuture<PermissionManagementResponse>>()
        whenever(future.getOrThrow(defaultTimeout)).thenThrow(IllegalArgumentException("Invalid user."))

        val capture = argumentCaptor<PermissionManagementRequest>()
        whenever(rpcSender.sendRequest(capture.capture())).thenReturn(future)

        val requestDto = RemovePropertyFromUserRequestDto("requestUserId", "user-login1", "email")

        val e = assertThrows<IllegalArgumentException> {
            manager.removePropertyFromUser(requestDto)
        }

        assertEquals("Invalid user.", e.message)
    }

    @Test
    fun `get user properties using the cache`() {
        whenever(permissionManagementCache.getUser("loginname123")).thenReturn(avroUser)
        val result = manager.getUserProperties(getUserPropertiesRequestDto)!!
        assertNotNull(result)
        assertEquals(userProperty.lastChangeDetails.updateTimestamp, result.first().lastChangedTimestamp)
        assertEquals(userProperty.key, result.first().key)
        assertEquals(userProperty.value, result.first().value)
    }

    @Test
    fun `get user properties returns empty set when user does not exist`() {
        whenever(permissionManagementCache.getUser("invalid-user-login-name")).thenReturn(null)

        val result = manager.getUserProperties(getUserPropertiesRequestDto)
        assertEquals(emptySet<PropertyResponseDto>(), result)
    }

    @Test
    fun `get users by property using the cache`() {
        whenever(permissionManagementCache.getUsersByProperty("email", "a@b.com")).thenReturn(setOf(avroUser))
        val result = manager.getUsersByProperty(getUsersByPropertyRequestDto).first()

        assertNotNull(result)
        assertEquals(fullName, result.fullName)
        assertEquals(avroUser.enabled, result.enabled)
        assertEquals(avroUser.lastChangeDetails.updateTimestamp, result.lastUpdatedTimestamp)
        assertEquals(avroUser.ssoAuth, result.ssoAuth)
        assertEquals(avroUser.parentGroupId, result.parentGroup)
        assertEquals(1, result.properties.size)

        val property = result.properties.first()
        assertEquals(userProperty.lastChangeDetails.updateTimestamp, property.lastChangedTimestamp)
        assertEquals(userProperty.key, property.key)
        assertEquals(userProperty.value, property.value)
    }

    @Test
    fun `get users by property returns empty set when property does not exist`() {
        whenever(permissionManagementCache.getUsersByProperty("invalid-key", "invalid-value")).thenReturn(null)

        val result = manager.getUsersByProperty(getUsersByPropertyRequestDto)
        assertEquals(emptySet<UserResponseDto>(), result)
    }
}
