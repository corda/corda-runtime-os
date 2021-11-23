package net.corda.libs.permissions.endpoints.v1.user.impl

import net.corda.httprpc.PluggableRPCOps
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
import org.osgi.service.component.annotations.Component

/**
 * An RPC Ops endpoint for User operations.
 */
@Component(service = [UserEndpoint::class, PluggableRPCOps::class])
class UserEndpointImpl : UserEndpoint {

    override val targetInterface: Class<UserEndpoint> = UserEndpoint::class.java

    override var permissionManager: PermissionManager? = null
    override var permissionValidator: PermissionValidator? = null

    @VisibleForTesting
    internal var running: Boolean = false

    override val protocolVersion = 1

    override fun createUser(createUserType: CreateUserType): UserResponseType {
        validatePermissionManager()

        val createUserResult = permissionManager!!.createUser(
            convertFromUserType(createUserType)
        )

        return createUserResult.getOrThrow()
            .convertToUserType()
    }

    override fun getUser(loginName: String): UserResponseType? {
        validatePermissionManager()
        val userResponseDto = permissionManager!!.getUser(
            GetUserRequestDto(
                "todo", // the endpoint needs more context to get the request user name
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
            "todo", // the endpoint needs more context to get the request user name
            createUserType.fullName,
            createUserType.loginName,
            createUserType.enabled,
            createUserType.initialPassword,
            createUserType.passwordExpiry,
            createUserType.parentGroup,
        )
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