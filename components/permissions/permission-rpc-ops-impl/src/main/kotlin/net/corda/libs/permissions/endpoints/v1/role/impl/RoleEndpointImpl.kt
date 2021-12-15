package net.corda.libs.permissions.endpoints.v1.role.impl

import net.corda.httprpc.PluggableRPCOps
import net.corda.httprpc.exception.HttpApiException
import net.corda.httprpc.exception.ResourceNotFoundException
import net.corda.httprpc.security.CURRENT_RPC_CONTEXT
import net.corda.libs.permissions.endpoints.common.PermissionEndpointEventHandler
import net.corda.libs.permissions.endpoints.v1.converter.convertToDto
import net.corda.libs.permissions.endpoints.v1.converter.convertToEndpointType
import net.corda.libs.permissions.endpoints.v1.role.RoleEndpoint
import net.corda.libs.permissions.endpoints.v1.role.types.CreateRoleType
import net.corda.libs.permissions.endpoints.v1.role.types.RoleResponseType
import net.corda.libs.permissions.manager.request.GetRoleRequestDto
import net.corda.lifecycle.Lifecycle
import net.corda.lifecycle.LifecycleCoordinatorFactory
import net.corda.lifecycle.createCoordinator
import net.corda.permissions.service.PermissionServiceComponent
import org.osgi.service.component.annotations.Activate
import org.osgi.service.component.annotations.Component
import org.osgi.service.component.annotations.Reference

/**
 * An RPC Ops endpoint for Role operations.
 */
@Component(service = [PluggableRPCOps::class])
class RoleEndpointImpl @Activate constructor(
    @Reference(service = LifecycleCoordinatorFactory::class)
    private val coordinatorFactory: LifecycleCoordinatorFactory,
    @Reference(service = PermissionServiceComponent::class)
    private val permissionServiceComponent: PermissionServiceComponent
) : RoleEndpoint, PluggableRPCOps<RoleEndpoint>, Lifecycle {

    override val targetInterface: Class<RoleEndpoint> = RoleEndpoint::class.java

    override val protocolVersion = 1

    private val coordinator = coordinatorFactory.createCoordinator<RoleEndpoint>(
        PermissionEndpointEventHandler("RoleEndpoint")
    )

    override fun createRole(createRoleType: CreateRoleType): RoleResponseType {
        validatePermissionManager()

        val rpcContext = CURRENT_RPC_CONTEXT.get()
        val principal = rpcContext.principal

        val createRoleResult = permissionServiceComponent.permissionManager.createRole(
            createRoleType.convertToDto(principal)
        )

        return createRoleResult.getOrThrow().convertToEndpointType()
    }

    override fun getRole(id: String): RoleResponseType {
        validatePermissionManager()

        val rpcContext = CURRENT_RPC_CONTEXT.get()
        val principal = rpcContext.principal

        val roleResponseDto = permissionServiceComponent.permissionManager.getRole(
            GetRoleRequestDto(principal, id)
        )

        return roleResponseDto?.convertToEndpointType() ?: throw ResourceNotFoundException("Role", id)
    }

    @Suppress("ThrowsCount")
    private fun validatePermissionManager() {
        if (!isRunning) {
            throw HttpApiException("Role Endpoint must be started.", 500)
        }

        if (!permissionServiceComponent.isRunning) {
            throw HttpApiException("Permission manager must be running.", 500)
        }
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