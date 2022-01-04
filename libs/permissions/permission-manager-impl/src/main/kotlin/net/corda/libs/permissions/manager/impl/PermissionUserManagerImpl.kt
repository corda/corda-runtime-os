package net.corda.libs.permissions.manager.impl

import net.corda.data.permissions.User
import net.corda.data.permissions.management.PermissionManagementRequest
import net.corda.data.permissions.management.PermissionManagementResponse
import net.corda.data.permissions.management.user.AddRoleToUserRequest
import net.corda.data.permissions.management.user.CreateUserRequest
import net.corda.data.permissions.management.user.RemoveRoleFromUserRequest
import net.corda.libs.configuration.SmartConfig
import net.corda.libs.permissions.cache.PermissionCache
import net.corda.libs.permissions.manager.PermissionUserManager
import net.corda.libs.permissions.manager.exception.PermissionManagerException
import net.corda.libs.permissions.manager.impl.SmartConfigUtil.getEndpointTimeout
import net.corda.libs.permissions.manager.impl.converter.convertToResponseDto
import net.corda.libs.permissions.manager.request.AddRoleToUserRequestDto
import net.corda.libs.permissions.manager.request.CreateUserRequestDto
import net.corda.libs.permissions.manager.request.GetUserRequestDto
import net.corda.libs.permissions.manager.request.RemoveRoleFromUserRequestDto
import net.corda.libs.permissions.manager.response.UserResponseDto
import net.corda.messaging.api.publisher.RPCSender
import net.corda.permissions.password.PasswordService
import net.corda.v5.base.concurrent.getOrThrow

class PermissionUserManagerImpl(
    config: SmartConfig,
    private val rpcSender: RPCSender<PermissionManagementRequest, PermissionManagementResponse>,
    private val permissionCache: PermissionCache,
    private val passwordService: PasswordService
) : PermissionUserManager {

    private val writerTimeout = config.getEndpointTimeout()

    override fun createUser(createUserRequestDto: CreateUserRequestDto): UserResponseDto {
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

        val futureResponse = future.getOrThrow(writerTimeout)

        val result = futureResponse.response
        if (result !is User)
            throw PermissionManagerException("Unknown response for Create User operation: $result")

        return result.convertToResponseDto()
    }

    override fun getUser(userRequestDto: GetUserRequestDto): UserResponseDto? {
        val cachedUser: User = permissionCache.getUser(userRequestDto.loginName) ?: return null
        return cachedUser.convertToResponseDto()
    }

    override fun addRoleToUser(addRoleToUserRequestDto: AddRoleToUserRequestDto): UserResponseDto {
        val future = rpcSender.sendRequest(
            PermissionManagementRequest(
                addRoleToUserRequestDto.requestedBy,
                null,
                AddRoleToUserRequest(
                    addRoleToUserRequestDto.loginName,
                    addRoleToUserRequestDto.roleId
                )
            )
        )
        val futureResponse = future.getOrThrow(writerTimeout)

        val result = futureResponse.response
        if (result !is User)
            throw PermissionManagerException("Unknown response for Add Role to User operation: $result")

        return result.convertToResponseDto()
    }

    override fun removeRoleFromUser(removeRoleFromUserRequestDto: RemoveRoleFromUserRequestDto): UserResponseDto {
        val future = rpcSender.sendRequest(
            PermissionManagementRequest(
                removeRoleFromUserRequestDto.requestedBy,
                null,
                RemoveRoleFromUserRequest(
                    removeRoleFromUserRequestDto.loginName,
                    removeRoleFromUserRequestDto.roleId
                )
            )
        )
        val futureResponse = future.getOrThrow(writerTimeout)

        val result = futureResponse.response
        if (result !is User)
            throw PermissionManagerException("Unknown response for Remove Role from User operation: $result")

        return result.convertToResponseDto()
    }
}