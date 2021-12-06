package net.corda.libs.permissions.endpoints.v1.user.impl

import net.corda.httprpc.PluggableRPCOps
import net.corda.libs.permissions.endpoints.exception.PermissionEndpointException
import net.corda.libs.permissions.endpoints.v1.user.UserEndpoint
import net.corda.libs.permissions.endpoints.v1.user.types.CreateUserType
import net.corda.libs.permissions.endpoints.v1.user.types.UserResponseType
import net.corda.libs.permissions.manager.request.CreateUserRequestDto
import net.corda.libs.permissions.manager.request.GetUserRequestDto
import net.corda.libs.permissions.manager.response.UserResponseDto
import net.corda.lifecycle.Lifecycle
import net.corda.lifecycle.LifecycleCoordinatorFactory
import net.corda.lifecycle.createCoordinator
import net.corda.permissions.service.PermissionServiceComponent
import org.osgi.service.component.annotations.Activate
import org.osgi.service.component.annotations.Component
import org.osgi.service.component.annotations.Reference

/**
 * An RPC Ops endpoint for User operations.
 */
@Component(service = [PluggableRPCOps::class])
class UserEndpointImpl @Activate constructor(
    @Reference(service = LifecycleCoordinatorFactory::class)
    private val coordinatorFactory: LifecycleCoordinatorFactory,
    @Reference(service = PermissionServiceComponent::class)
    private val permissionServiceComponent: PermissionServiceComponent
) : UserEndpoint, PluggableRPCOps<UserEndpoint>, Lifecycle {

    override val targetInterface: Class<UserEndpoint> = UserEndpoint::class.java

    override val protocolVersion = 1

    private val coordinator = coordinatorFactory.createCoordinator<UserEndpoint>(UserEndpointImplEventHandler())

    override fun createUser(createUserType: CreateUserType): UserResponseType {
        validatePermissionManager()

        val createUserResult = permissionServiceComponent.permissionManager.createUser(
            convertFromUserType(createUserType)
        )

        return createUserResult.getOrThrow().convertToUserType()
    }

    override fun getUser(loginName: String): UserResponseType? {
        validatePermissionManager()
        val userResponseDto = permissionServiceComponent.permissionManager.getUser(
            GetUserRequestDto(
                "todo", // the endpoint needs more context to get the request user name
                loginName
            )
        )
        return userResponseDto?.convertToUserType()
    }

    @Suppress("ThrowsCount")
    private fun validatePermissionManager() {
        if (!isRunning) {
            throw PermissionEndpointException("User Endpoint must be started.", 500)
        }

        if (!permissionServiceComponent.isRunning) {
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
        get() = coordinator.isRunning

    override fun start() {
        coordinator.start()
    }

    override fun stop() {
        coordinator.close()
    }
}