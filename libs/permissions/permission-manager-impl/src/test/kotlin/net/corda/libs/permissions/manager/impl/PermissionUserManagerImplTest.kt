package net.corda.libs.permissions.manager.impl

import java.time.Duration
import java.time.Instant
import java.util.UUID
import java.util.concurrent.CompletableFuture
import net.corda.data.permissions.ChangeDetails
import net.corda.data.permissions.Property
import net.corda.data.permissions.User
import net.corda.data.permissions.management.PermissionManagementRequest
import net.corda.data.permissions.management.PermissionManagementResponse
import net.corda.data.permissions.management.user.CreateUserRequest
import net.corda.libs.permissions.cache.PermissionCache
import net.corda.libs.permissions.manager.exception.PermissionManagerException
import net.corda.libs.permissions.manager.request.CreateUserRequestDto
import net.corda.libs.permissions.manager.request.GetUserRequestDto
import net.corda.messaging.api.publisher.RPCSender
import net.corda.v5.base.concurrent.getOrThrow
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test

import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

class PermissionUserManagerImplTest {

    private val rpcSender = mock<RPCSender<PermissionManagementRequest, PermissionManagementResponse>>()
    private val permissionCache = mock<PermissionCache>()

    private val fullName = "first last"
    private val requestUserName = "requestUserName"
    private val passwordExpiry = Instant.now()
    private val parentGroup = "some-parent-group"
    private val createUserRequestDto = CreateUserRequestDto(requestedBy = requestUserName, virtualNodeId = "virtNode1",
        loginName = "loginname123", fullName = fullName, enabled = true, initialPassword = "mypassword", passwordExpiry = passwordExpiry,
        parentGroup = parentGroup)
    private val getUserRequestDto = GetUserRequestDto(requestedBy = requestUserName, virtualNodeId = "virtNode1",
        loginName = "loginname123")
    private val userProperty = Property(UUID.randomUUID().toString(), 0, ChangeDetails(Instant.now(), requestUserName), "email",
        "a@b.com")
    private val avroUser = User(UUID.randomUUID().toString(), 0, ChangeDetails(Instant.now(), requestUserName), fullName,
        true, "temp-hashed-password", "temporary-salt", false, parentGroup, listOf(userProperty),
        listOf("roleId1"))
    private val permissionManagementResponse = PermissionManagementResponse(avroUser)


    private val manager = PermissionUserManagerImpl(rpcSender, permissionCache)

    @Test
    fun `create a user sends rpc request and converts result`() {
        val future = mock<CompletableFuture<PermissionManagementResponse>>()
        whenever(future.getOrThrow(Duration.ofSeconds(10))).thenReturn(permissionManagementResponse)

        val requestCaptor = argumentCaptor<PermissionManagementRequest>()
        whenever(rpcSender.sendRequest(requestCaptor.capture())).thenReturn(future)

        val result = manager.createUser(createUserRequestDto)

        val requestCapture = requestCaptor.firstValue
        assertEquals(requestUserName, requestCapture.requestUserId)
        assertEquals("virtNode1", requestCapture.virtualNodeId)

        val avroCreateUserRequest = requestCapture.request as CreateUserRequest
        assertEquals(createUserRequestDto.loginName, avroCreateUserRequest.loginName)
        assertEquals(createUserRequestDto.fullName, avroCreateUserRequest.fullName)
        assertEquals(createUserRequestDto.enabled, avroCreateUserRequest.enabled)
        assertEquals("temp-hashed-password", avroCreateUserRequest.initialHashedPassword) // todo - hashing
        assertEquals("temporary-salt", avroCreateUserRequest.saltValue)
        assertNotNull(avroCreateUserRequest.passwordExpiry)
        assertEquals(createUserRequestDto.passwordExpiry!!.toEpochMilli(), avroCreateUserRequest.passwordExpiry.toEpochMilli())
        assertEquals(createUserRequestDto.parentGroup, avroCreateUserRequest.parentGroupId)

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
    fun `create a user throws exception if result is not an avro User`() {
        val future = mock<CompletableFuture<PermissionManagementResponse>>()
        val incorrectResponse = PermissionManagementResponse(true)
        whenever(future.getOrThrow(Duration.ofSeconds(10))).thenReturn(incorrectResponse)

        val requestCaptor = argumentCaptor<PermissionManagementRequest>()
        whenever(rpcSender.sendRequest(requestCaptor.capture())).thenReturn(future)

        assertThrows(PermissionManagerException::class.java) {
            manager.createUser(createUserRequestDto)
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
}