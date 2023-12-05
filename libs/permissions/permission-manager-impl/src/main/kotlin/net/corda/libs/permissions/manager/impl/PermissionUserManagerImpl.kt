package net.corda.libs.permissions.manager.impl

import net.corda.data.permissions.User
import net.corda.data.permissions.management.PermissionManagementRequest
import net.corda.data.permissions.management.PermissionManagementResponse
import net.corda.data.permissions.management.user.AddRoleToUserRequest
import net.corda.data.permissions.management.user.ChangeUserPasswordSelfRequest
import net.corda.data.permissions.management.user.ChangeUserPasswordOtherRequest
import net.corda.data.permissions.management.user.CreateUserRequest
import net.corda.data.permissions.management.user.RemoveRoleFromUserRequest
import net.corda.libs.configuration.SmartConfig
import net.corda.libs.permissions.management.cache.PermissionManagementCache
import net.corda.libs.permissions.validation.cache.PermissionValidationCache
import net.corda.libs.permissions.manager.PermissionUserManager
import net.corda.libs.permissions.manager.impl.SmartConfigUtil.getEndpointTimeout
import net.corda.libs.permissions.manager.impl.converter.convertToResponseDto
import net.corda.libs.permissions.manager.request.AddRoleToUserRequestDto
import net.corda.libs.permissions.manager.request.ChangeUserPasswordDto
import net.corda.libs.permissions.manager.request.CreateUserRequestDto
import net.corda.libs.permissions.manager.request.GetPermissionSummaryRequestDto
import net.corda.libs.permissions.manager.request.GetUserRequestDto
import net.corda.libs.permissions.manager.request.RemoveRoleFromUserRequestDto
import net.corda.libs.permissions.manager.response.UserPermissionSummaryResponseDto
import net.corda.libs.permissions.manager.response.UserResponseDto
import net.corda.messaging.api.publisher.RPCSender
import net.corda.permissions.password.PasswordHash
import net.corda.permissions.password.PasswordService
import net.corda.schema.configuration.ConfigKeys
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.concurrent.atomic.AtomicReference

class PermissionUserManagerImpl(
    restConfig: SmartConfig,
    rbacConfig: SmartConfig,
    private val rpcSender: RPCSender<PermissionManagementRequest, PermissionManagementResponse>,
    private val permissionManagementCacheRef: AtomicReference<PermissionManagementCache?>,
    private val permissionValidationCacheRef: AtomicReference<PermissionValidationCache?>,
    private val passwordService: PasswordService
) : PermissionUserManager {

    private val writerTimeout = restConfig.getEndpointTimeout()
    private val selfUserPasswordExpiryDays = rbacConfig.getInt(ConfigKeys.RBAC_USER_PASSWORD_CHANGE_EXPIRY)
    private val otherUserPasswordExpiryDays = rbacConfig.getInt(ConfigKeys.RBAC_ADMIN_PASSWORD_CHANGE_EXPIRY)

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

    override fun changeUserPasswordSelf(changeUserPasswordDto: ChangeUserPasswordDto): UserResponseDto {
        val saltAndHash = validatePasswordAndGetHash(changeUserPasswordDto.username, changeUserPasswordDto.newPassword)

        val result = sendPermissionWriteRequest<User>(
            rpcSender,
            writerTimeout,
            PermissionManagementRequest(
                changeUserPasswordDto.requestedBy,
                null,
                ChangeUserPasswordSelfRequest(
                    changeUserPasswordDto.requestedBy,
                    saltAndHash.salt,
                    saltAndHash.value,
                    Instant.now().plus(selfUserPasswordExpiryDays.toLong(), ChronoUnit.DAYS)
                )
            )
        )

        return result.convertToResponseDto()
    }

    override fun changeUserPasswordOther(changeUserPasswordDto: ChangeUserPasswordDto): UserResponseDto {
        val saltAndHash = validatePasswordAndGetHash(changeUserPasswordDto.username, changeUserPasswordDto.newPassword)

        val result = sendPermissionWriteRequest<User>(
            rpcSender,
            writerTimeout,
            PermissionManagementRequest(
                changeUserPasswordDto.requestedBy,
                null,
                ChangeUserPasswordOtherRequest(
                    changeUserPasswordDto.requestedBy,
                    changeUserPasswordDto.username,
                    saltAndHash.salt,
                    saltAndHash.value,
                    Instant.now().plus(otherUserPasswordExpiryDays.toLong(), ChronoUnit.DAYS)
                )
            )
        )

        return result.convertToResponseDto()
    }

    private fun validatePasswordAndGetHash(username: String, newPassword: String) : PasswordHash {
        val permissionManagementCache = checkNotNull(permissionManagementCacheRef.get()) {
            "Permission management cache is null."
        }
        val cachedUser: User = permissionManagementCache.getUser(username)
            ?: throw IllegalStateException("Could not find user with username ${username}")

        val saltAndHash = passwordService.saltAndHash(newPassword)

        if (saltAndHash.value == cachedUser.hashedPassword) {
            throw IllegalArgumentException("New password must be different from current one.")
        }

        return saltAndHash
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