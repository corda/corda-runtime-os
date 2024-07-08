package net.corda.libs.permissions.manager.impl

import net.corda.data.permissions.User
import net.corda.data.permissions.management.PermissionManagementRequest
import net.corda.data.permissions.management.PermissionManagementResponse
import net.corda.data.permissions.management.user.AddRoleToUserRequest
import net.corda.data.permissions.management.user.ChangeUserPasswordRequest
import net.corda.data.permissions.management.user.CreateUserRequest
import net.corda.data.permissions.management.user.DeleteUserRequest
import net.corda.data.permissions.management.user.RemoveRoleFromUserRequest
import net.corda.libs.configuration.SmartConfig
import net.corda.libs.permissions.management.cache.PermissionManagementCache
import net.corda.libs.permissions.manager.PermissionUserManager
import net.corda.libs.permissions.manager.impl.SmartConfigUtil.getEndpointTimeout
import net.corda.libs.permissions.manager.impl.converter.convertToResponseDto
import net.corda.libs.permissions.manager.request.*
import net.corda.libs.permissions.manager.response.UserPermissionSummaryResponseDto
import net.corda.libs.permissions.manager.response.UserResponseDto
import net.corda.libs.permissions.validation.cache.PermissionValidationCache
import net.corda.messaging.api.publisher.RPCSender
import net.corda.permissions.password.PasswordHash
import net.corda.permissions.password.PasswordService
import net.corda.schema.configuration.ConfigKeys
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.concurrent.atomic.AtomicReference

@Suppress("LongParameterList")
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
    private val passwordLengthLimit = rbacConfig.getInt(ConfigKeys.RBAC_PASSWORD_LENGTH_LIMIT)

    override fun createUser(createUserRequestDto: CreateUserRequestDto): UserResponseDto {
        val saltAndHash = createUserRequestDto.initialPassword?.let {
            checkLength(it)
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

    override fun deleteUser(deleteUserRequestDto: DeleteUserRequestDto): UserResponseDto {
        val result = sendPermissionWriteRequest<User>(
            rpcSender,
            writerTimeout,
            PermissionManagementRequest(
                deleteUserRequestDto.requestedBy,
                null,
                DeleteUserRequest(
                    deleteUserRequestDto.loginName
                )
            )
        )

        return result.convertToResponseDto()
    }

    override fun changeUserPasswordSelf(changeUserPasswordDto: ChangeUserPasswordDto): UserResponseDto =
        changeUserPassword(changeUserPasswordDto, selfUserPasswordExpiryDays)

    override fun changeUserPasswordOther(changeUserPasswordDto: ChangeUserPasswordDto): UserResponseDto =
        changeUserPassword(changeUserPasswordDto, otherUserPasswordExpiryDays)

    private fun changeUserPassword(changeUserPasswordDto: ChangeUserPasswordDto, expiryDays: Int): UserResponseDto {
        val saltAndHash = validatePasswordAndGetHash(changeUserPasswordDto.username, changeUserPasswordDto.newPassword)

        val result = sendPermissionWriteRequest<User>(
            rpcSender,
            writerTimeout,
            PermissionManagementRequest(
                changeUserPasswordDto.requestedBy,
                null,
                ChangeUserPasswordRequest(
                    changeUserPasswordDto.requestedBy,
                    changeUserPasswordDto.username,
                    saltAndHash.salt,
                    saltAndHash.value,
                    Instant.now().plus(expiryDays.toLong(), ChronoUnit.DAYS)
                )
            )
        )

        return result.convertToResponseDto()
    }

    @Suppress("ThrowsCount")
    private fun validatePasswordAndGetHash(username: String, newPassword: String): PasswordHash {
        if (newPassword.isBlank()) {
            throw IllegalArgumentException("The passphrase must not be blank string.")
        }

        checkLength(newPassword)

        val permissionManagementCache = checkNotNull(permissionManagementCacheRef.get()) {
            "Permission management cache is null."
        }
        val cachedUser: User = permissionManagementCache.getUser(username)
            ?: throw NoSuchElementException("Could not find user with username $username")

        val cachedPasswordHash = PasswordHash(cachedUser.saltValue, cachedUser.hashedPassword)

        if (passwordService.verifies(newPassword, cachedPasswordHash)) {
            throw IllegalArgumentException("New password must be different from the current one.")
        }

        return passwordService.saltAndHash(newPassword)
    }

    private fun checkLength(password: String) {
        if (password.length > passwordLengthLimit) {
            throw IllegalArgumentException("Password exceed current length limit of $passwordLengthLimit.")
        }
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

    override fun addPropertyToUser(addPropertyToUserRequestDto: AddPropertyToUserRequestDto): UserResponseDto {
        val result = sendPermissionWriteRequest<User>(
            rpcSender,
            writerTimeout,
            PermissionManagementRequest(
                AddPropertyToUserRequestDto.requestedBy,
                null,
                AddPropertyToUserRequest(
                    addPropertyToUserRequestDto.loginName,
                    addPropertyToUserRequestDto.properties
                )
            )
        )
        return result.convertToResponseDto()
    }
}
