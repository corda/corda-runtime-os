package net.corda.libs.permissions.manager.impl

import java.time.Duration
import net.corda.data.permissions.User
import net.corda.data.permissions.management.PermissionManagementRequest
import net.corda.data.permissions.management.PermissionManagementResponse
import net.corda.data.permissions.management.user.CreateUserRequest
import net.corda.libs.permissions.cache.PermissionCache
import net.corda.libs.permissions.manager.PermissionUserManager
import net.corda.libs.permissions.manager.exception.PermissionManagerException
import net.corda.libs.permissions.manager.impl.converter.convertAvroUserToUserResponseDto
import net.corda.libs.permissions.manager.request.CreateUserRequestDto
import net.corda.libs.permissions.manager.request.GetUserRequestDto
import net.corda.libs.permissions.manager.response.UserResponseDto
import net.corda.messaging.api.publisher.RPCSender
import net.corda.v5.base.concurrent.getOrThrow

class PermissionUserManagerImpl(
    private val rpcSender: RPCSender<PermissionManagementRequest, PermissionManagementResponse>,
    private val permissionCache: PermissionCache
) : PermissionUserManager {

    override fun createUser(createUserRequestDto: CreateUserRequestDto): UserResponseDto {
        val future = rpcSender.sendRequest(
            PermissionManagementRequest(
                createUserRequestDto.requestedBy,
                createUserRequestDto.virtualNodeId,
                CreateUserRequest(
                    createUserRequestDto.fullName,
                    createUserRequestDto.loginName,
                    createUserRequestDto.enabled,
                    "temp-hashed-password", // todo perform hashing and salting
                    "temporary-salt",
                    createUserRequestDto.passwordExpiry,
                    createUserRequestDto.parentGroup
                )
            )
        )

        val futureResponse = future.getOrThrow(Duration.ofSeconds(10))

        val result = futureResponse.response
        if (result is User) {
            return result.convertAvroUserToUserResponseDto()
        } else {
            throw PermissionManagerException("Unknown response for Create User operation: $result")
        }
    }

    override fun getUser(userRequestDto: GetUserRequestDto): UserResponseDto? {
        val cachedUser: User = permissionCache.getUser(userRequestDto.loginName) ?: return null
        return cachedUser.convertAvroUserToUserResponseDto()
    }
}