package net.corda.libs.permissions.endpoints.v1.user.impl

import net.corda.libs.permission.PermissionValidator
import net.corda.libs.permissions.endpoints.exception.PermissionEndpointException
import net.corda.libs.permissions.endpoints.v1.user.UserEndpoint
import net.corda.libs.permissions.endpoints.v1.user.types.CreateUserType
import net.corda.libs.permissions.endpoints.v1.user.types.UserResponseType
import net.corda.libs.permissions.manager.PermissionManager
import net.corda.libs.permissions.manager.request.CreateUserRequestDto
import net.corda.libs.permissions.manager.request.GetUserRequestDto
import net.corda.libs.permissions.manager.response.UserResponseDto
import net.corda.v5.base.annotations.VisibleForTesting

/**
 * An RPC Ops endpoint for User operations.
 *
 * @param permissionManager for performing permission management operations.
 * @param permissionValidator for performing additional permission validation for certain operations.
 */
class UserEndpointImpl(
    private var permissionManager: PermissionManager?,
    private var permissionValidator: PermissionValidator?
) : UserEndpoint {

    @VisibleForTesting
    internal var running: Boolean = false

    override val protocolVersion = 1

    override fun createUser(virtualNodeId: String, createUserType: CreateUserType): UserResponseType {
        validatePermissionManager()

        val createUserResult = permissionManager!!.createUser(
            convertFromUserType(createUserType)
        )

        return createUserResult.getOrThrow()
            .convertToUserType()
    }

    override fun getUser(virtualNodeId: String, loginName: String): UserResponseType? {
        validatePermissionManager()
        val userResponseDto = permissionManager!!.getUser(
            GetUserRequestDto(
                "todo",
                virtualNodeId,
                loginName
            )
        )
        return userResponseDto?.convertToUserType()
    }

    @Suppress("ThrowsCount")
    private fun validatePermissionManager() {
        if (!running) {
            throw PermissionEndpointException("User Endpoint must be started.", 500)
        }
        if (permissionManager == null) {
            throw PermissionEndpointException("Permission manager must be initialized.", 500)
        }
        if (!permissionManager!!.isRunning) {
            throw PermissionEndpointException("Permission manager must be running.", 500)
        }
    }

    private fun UserResponseDto.convertToUserType(): UserResponseType {
        return UserResponseType(
            id,
            version,
            lastUpdatedTimestamp,
            fullName,
            loginName,
            enabled,
            passwordExpiry,
            parentGroup
        )
    }

    private fun convertFromUserType(createUserType: CreateUserType): CreateUserRequestDto {
        return CreateUserRequestDto(
            createUserType.requestedBy,
            createUserType.virtualNodeId,
            createUserType.fullName,
            createUserType.loginName,
            createUserType.enabled,
            createUserType.initialPassword,
            createUserType.passwordExpiry,
            createUserType.parentGroup,
        )
    }

    /**
     * Expose a setter to control the instance of the permission manager.
     */
    fun setPermissionManager(permissionManager: PermissionManager?) {
        this.permissionManager = permissionManager
    }

    /**
     * Expose a setter to control the instance of the permission validator.
     */
    fun setPermissionValidator(permissionValidator: PermissionValidator?) {
        this.permissionValidator = permissionValidator
    }

    override val isRunning: Boolean
        get() = running

    override fun start() {
        running = true
    }

    override fun stop() {
        running = false
        permissionManager = null
        permissionValidator = null
    }
}