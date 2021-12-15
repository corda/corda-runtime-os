package net.corda.libs.permissions.manager.impl

import com.typesafe.config.ConfigValueFactory
import java.time.Duration
import java.time.Instant
import java.util.UUID
import java.util.concurrent.CompletableFuture
import net.corda.data.permissions.ChangeDetails
import net.corda.data.permissions.Property
import net.corda.data.permissions.RoleAssociation
import net.corda.data.permissions.User
import net.corda.data.permissions.management.PermissionManagementRequest
import net.corda.data.permissions.management.PermissionManagementResponse
import net.corda.data.permissions.management.user.CreateUserRequest
import net.corda.libs.configuration.SmartConfig
import net.corda.libs.configuration.SmartConfigImpl
import net.corda.libs.permissions.cache.PermissionCache
import net.corda.libs.permissions.manager.exception.PermissionManagerException
import net.corda.libs.permissions.manager.request.CreateUserRequestDto
import net.corda.libs.permissions.manager.request.GetUserRequestDto
import net.corda.messaging.api.publisher.RPCSender
import net.corda.permissions.password.PasswordHash
import net.corda.permissions.password.PasswordService
import net.corda.v5.base.concurrent.getOrThrow
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any

import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

class PermissionUserManagerImplTest {

    private val rpcSender = mock<RPCSender<PermissionManagementRequest, PermissionManagementResponse>>()
    private val permissionCache = mock<PermissionCache>()
    private val passwordService = mock<PasswordService>()

    private val fullName = "first last"
    private val requestUserName = "requestUserName"
    private val passwordExpiry = Instant.now()
    private val parentGroup = "some-parent-group"

    private val createUserRequestDto = CreateUserRequestDto(requestedBy = requestUserName, loginName = "loginname123", fullName = fullName,
        enabled = true, initialPassword = "mypassword", passwordExpiry = passwordExpiry, parentGroup = parentGroup)
    private val createUserRequestDtoWithoutPassword = CreateUserRequestDto(requestedBy = requestUserName, loginName = "loginname123",
        fullName = fullName, enabled = true, initialPassword = null, passwordExpiry = null, parentGroup = parentGroup)

    private val userCreationTime = Instant.now()
    private val getUserRequestDto = GetUserRequestDto(requestedBy = requestUserName, loginName = "loginname123")
    private val userProperty = Property(UUID.randomUUID().toString(), 0, ChangeDetails(userCreationTime), "email",
        "a@b.com")

    private val avroUser = User(UUID.randomUUID().toString(), 0, ChangeDetails(userCreationTime), "user-login1", fullName, true,
        "temp-hashed-password", "temporary-salt", userCreationTime, false, parentGroup, listOf(userProperty),
        listOf(RoleAssociation(ChangeDetails(userCreationTime), "roleId1")))

    private val avroUserWithoutPassword = User(UUID.randomUUID().toString(), 0, ChangeDetails(userCreationTime),
        "user-login2", fullName, true, null, null, null,
        true, parentGroup, listOf(userProperty), listOf(RoleAssociation(ChangeDetails(userCreationTime), "roleId1")))

    private val permissionManagementResponse = PermissionManagementResponse(avroUser)
    private val permissionManagementResponseWithoutPassword = PermissionManagementResponse(avroUserWithoutPassword)
    private val config = mock<SmartConfig>()

    private val manager = PermissionUserManagerImpl(config, rpcSender, permissionCache, passwordService)

    @Test
    fun `create a user sends rpc request and converts result`() {
        val future = mock<CompletableFuture<PermissionManagementResponse>>()
        whenever(future.getOrThrow(Duration.ofSeconds(10))).thenReturn(permissionManagementResponse)

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
        assertEquals(createUserRequestDto.passwordExpiry!!.toEpochMilli(), capturedCreateUserRequest.passwordExpiry.toEpochMilli())
        assertEquals(createUserRequestDto.parentGroup, capturedCreateUserRequest.parentGroupId)

        assertTrue(result.isSuccess)
        assertFalse(result.isFailure)
        result.doOnSuccess {
            assertEquals(fullName, it.fullName)
            assertEquals(avroUser.enabled, it.enabled)
            assertEquals(avroUser.lastChangeDetails.updateTimestamp, it.lastUpdatedTimestamp)
            assertEquals(false, it.ssoAuth)
            assertEquals(avroUser.parentGroupId, it.parentGroup)
            assertEquals(1, it.properties.size)

            val property = it.properties.first()
            assertEquals(userProperty.lastChangeDetails.updateTimestamp, property.lastChangedTimestamp)
            assertEquals(userProperty.key, property.key)
            assertEquals(userProperty.value, property.value)
        }
    }

    @Test
    fun `create a user sends rpc request and converts result correctly when no password is provided`() {
        val future = mock<CompletableFuture<PermissionManagementResponse>>()
        whenever(future.getOrThrow(Duration.ofSeconds(10))).thenReturn(permissionManagementResponseWithoutPassword)

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

        assertTrue(result.isSuccess)
        assertFalse(result.isFailure)
        result.doOnSuccess {
            assertEquals(fullName, it.fullName)
            assertEquals(avroUser.enabled, it.enabled)
            assertEquals(avroUser.lastChangeDetails.updateTimestamp, it.lastUpdatedTimestamp)
            assertEquals(true, it.ssoAuth)
            assertEquals(avroUser.parentGroupId, it.parentGroup)
            assertEquals(1, it.properties.size)

            val property = it.properties.first()
            assertEquals(userProperty.lastChangeDetails.updateTimestamp, property.lastChangedTimestamp)
            assertEquals(userProperty.key, property.key)
            assertEquals(userProperty.value, property.value)
        }
    }

    @Test
    fun `create a user throws exception if result is not an avro User`() {
        val future = mock<CompletableFuture<PermissionManagementResponse>>()
        val incorrectResponse = PermissionManagementResponse(true)
        whenever(future.getOrThrow(Duration.ofSeconds(10))).thenReturn(incorrectResponse)

        val requestCaptor = argumentCaptor<PermissionManagementRequest>()
        whenever(rpcSender.sendRequest(requestCaptor.capture())).thenReturn(future)

        val result = manager.createUser(createUserRequestDto)

        assertTrue(result.isFailure)
        assertFalse(result.isSuccess)
        assertThrows(PermissionManagerException::class.java) {
            result.getOrThrow()
        }
    }

    @Test
    fun `get a user uses the cache and converts avro user to dto`() {
        whenever(permissionCache.getUser("loginname123")).thenReturn(avroUser)

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
        whenever(permissionCache.getUser("invalid-user-login-name")).thenReturn(null)

        val result = manager.getUser(getUserRequestDto)

        assertNull(result)
    }

    @Test
    fun `creating permission user manager will use the remote writer timeout set in the config`() {
        val config = SmartConfigImpl.empty()
            .withValue("endpointTimeoutMs", ConfigValueFactory.fromAnyRef(12345L))

        val future = mock<CompletableFuture<PermissionManagementResponse>>()
        val requestCaptor = argumentCaptor<PermissionManagementRequest>()
        whenever(rpcSender.sendRequest(requestCaptor.capture())).thenReturn(future)
        whenever(passwordService.saltAndHash(eq("mypassword"))).thenReturn(PasswordHash("randomSalt", "hashedPass"))
        whenever(future.getOrThrow(Duration.ofMillis(12345L))).thenReturn(permissionManagementResponse)

        val manager = PermissionUserManagerImpl(config, rpcSender, permissionCache, passwordService)

        val result = manager.createUser(createUserRequestDto)

        verify(future, times(1)).getOrThrow(Duration.ofMillis(12345L))

        assertTrue(result.isSuccess)
        assertEquals(avroUser.id, result.getOrThrow().id)
    }
}