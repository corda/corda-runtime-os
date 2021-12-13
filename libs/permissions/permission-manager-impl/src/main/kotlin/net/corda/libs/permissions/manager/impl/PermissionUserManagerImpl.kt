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
import net.corda.permissions.password.PasswordService
import net.corda.v5.base.concurrent.getOrThrow
import net.corda.v5.base.util.Try

class PermissionUserManagerImpl(
    private val rpcSender: RPCSender<PermissionManagementRequest, PermissionManagementResponse>,
    private val permissionCache: PermissionCache,
    private val passwordService: PasswordService
) : PermissionUserManager {

    override fun createUser(createUserRequestDto: CreateUserRequestDto): Try<UserResponseDto> {
        return Try.on {
            val saltAndHash = createUserRequestDto.initialPassword?.let {
                passwordService.saltAndHash(it)
            }

            val future = rpcSender.sendRequest(
                PermissionManagementRequest(
                    createUserRequestDto.requestedBy,
                    "cluster",
                    CreateUserRequest(
                        createUserRequestDto.fullName,
                        createUserRequestDto.loginName,
                        createUserRequestDto.enabled,
                        saltAndHash?.value,
                        saltAndHash?.salt,
                        createUserRequestDto.passwordExpiry,
                        createUserRequestDto.parentGroup
                    )
                )
            )

            val futureResponse = future.getOrThrow(Duration.ofSeconds(10))

            val result = futureResponse.response
            if (result !is User)
                throw PermissionManagerException("Unknown response for Create User operation: $result")

            result.convertAvroUserToUserResponseDto()
        }
    }

    override fun getUser(userRequestDto: GetUserRequestDto): UserResponseDto? {
        val cachedUser: User = permissionCache.getUser(userRequestDto.loginName) ?: return null
        return cachedUser.convertAvroUserToUserResponseDto()
    }
}