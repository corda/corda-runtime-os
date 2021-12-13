package net.corda.libs.permissions.endpoints.v1.user.impl

import net.corda.httprpc.PluggableRPCOps
import net.corda.httprpc.exception.HttpApiException
import net.corda.httprpc.exception.ResourceNotFoundException
import net.corda.httprpc.security.CURRENT_RPC_CONTEXT
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

    override fun getUser(loginName: String): UserResponseType {
        validatePermissionManager()
        val rpcContext = CURRENT_RPC_CONTEXT.get()
        val principal = rpcContext.principal
        val userResponseDto = permissionServiceComponent.permissionManager.getUser(
            GetUserRequestDto(principal, loginName)
        )

        return userResponseDto?.convertToUserType() ?: throw ResourceNotFoundException("User", loginName)
    }

    @Suppress("ThrowsCount")
    private fun validatePermissionManager() {
        if (!isRunning) {
            throw HttpApiException("User Endpoint must be started.", 500)
        }

        if (!permissionServiceComponent.isRunning) {
            throw HttpApiException("Permission manager must be running.", 500)
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
        val rpcContext = CURRENT_RPC_CONTEXT.get()
        val principal = rpcContext.principal
        return CreateUserRequestDto(
            principal,
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