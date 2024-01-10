package net.corda.libs.permissions.manager.impl

import net.corda.data.permissions.User
import net.corda.data.permissions.management.PermissionManagementRequest
import net.corda.data.permissions.management.PermissionManagementResponse
import net.corda.data.permissions.management.user.AddRoleToUserRequest
import net.corda.data.permissions.management.user.CreateUserRequest
import net.corda.data.permissions.management.user.RemoveRoleFromUserRequest
import net.corda.libs.configuration.SmartConfig
import net.corda.libs.permissions.management.cache.PermissionManagementCache
import net.corda.libs.permissions.validation.cache.PermissionValidationCache
import net.corda.libs.permissions.manager.PermissionUserManager
import net.corda.libs.permissions.manager.impl.SmartConfigUtil.getEndpointTimeout
import net.corda.libs.permissions.manager.impl.converter.convertToResponseDto
import net.corda.libs.permissions.manager.request.AddRoleToUserRequestDto
import net.corda.libs.permissions.manager.request.CreateUserRequestDto
import net.corda.libs.permissions.manager.request.GetPermissionSummaryRequestDto
import net.corda.libs.permissions.manager.request.GetUserRequestDto
import net.corda.libs.permissions.manager.request.RemoveRoleFromUserRequestDto
import net.corda.libs.permissions.manager.response.UserPermissionSummaryResponseDto
import net.corda.libs.permissions.manager.response.UserResponseDto
import net.corda.messaging.api.publisher.RPCSender
import net.corda.permissions.password.PasswordService
import java.util.concurrent.atomic.AtomicReference

class PermissionUserManagerImpl(
    config: SmartConfig,
    private val rpcSender: RPCSender<PermissionManagementRequest, PermissionManagementResponse>,
    private val permissionManagementCacheRef: AtomicReference<PermissionManagementCache?>,
    private val permissionValidationCacheRef: AtomicReference<PermissionValidationCache?>,
    private val passwordService: PasswordService
) : PermissionUserManager {

    private val writerTimeout = config.getEndpointTimeout()

    override fun createUser(createUserRequestDto: CreateUserRequestDto): UserResponseDto {
        val saltAndHash = createUserRequestDto.initialPassword?.let {
            passwordService.saltAndHash(it)
        }

        val result = sendPermissionWriteRequest<User>(
            rpcSender,
            writerTimeout,
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

        return result.convertToResponseDto()
    }

    override fun getUser(userRequestDto: GetUserRequestDto): UserResponseDto? {
        val permissionManagementCache = checkNotNull(permissionManagementCacheRef.get()) {
            "Permission management cache is null."
        }
        val cachedUser: User = permissionManagementCache.getUser(userRequestDto.loginName) ?: return null
        return cachedUser.convertToResponseDto()
    }

    override fun addRoleToUser(addRoleToUserRequestDto: AddRoleToUserRequestDto): UserResponseDto {
        val result = sendPermissionWriteRequest<User>(
            rpcSender,
            writerTimeout,
            PermissionManagementRequest(
                addRoleToUserRequestDto.requestedBy,
                null,
                AddRoleToUserRequest(
                    addRoleToUserRequestDto.loginName,
                    addRoleToUserRequestDto.roleId
                )
            )
        )

        return result.convertToResponseDto()
    }

    override fun removeRoleFromUser(removeRoleFromUserRequestDto: RemoveRoleFromUserRequestDto): UserResponseDto {
        val result = sendPermissionWriteRequest<User>(
            rpcSender,
            writerTimeout,
            PermissionManagementRequest(
                removeRoleFromUserRequestDto.requestedBy,
                null,
                RemoveRoleFromUserRequest(
                    removeRoleFromUserRequestDto.loginName,
                    removeRoleFromUserRequestDto.roleId
                )
            )
        )

        return result.convertToResponseDto()
    }

    override fun getPermissionSummary(permissionSummaryRequestDto: GetPermissionSummaryRequestDto): UserPermissionSummaryResponseDto? {

        val permissionValidationCache = checkNotNull(permissionValidationCacheRef.get()) {
            "Permission validation cache is null."
        }

        val cachedPermissionSummary = permissionValidationCache.getPermissionSummary(permissionSummaryRequestDto.userLogin) ?: return null

        return UserPermissionSummaryResponseDto(
            cachedPermissionSummary.loginName,
            cachedPermissionSummary.enabled,
            cachedPermissionSummary.permissions.map { it.convertToResponseDto() },
            cachedPermissionSummary.lastUpdateTimestamp
        )
    }
}