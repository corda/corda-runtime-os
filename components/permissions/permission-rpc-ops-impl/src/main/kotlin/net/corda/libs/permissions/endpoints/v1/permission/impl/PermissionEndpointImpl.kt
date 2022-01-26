package net.corda.libs.permissions.endpoints.v1.permission.impl

import net.corda.httprpc.PluggableRPCOps
import net.corda.httprpc.exception.ResourceNotFoundException
import net.corda.httprpc.security.CURRENT_RPC_CONTEXT
import net.corda.libs.permissions.endpoints.common.PermissionEndpointEventHandler
import net.corda.libs.permissions.endpoints.common.PermissionManagementHandler.withPermissionManager
import net.corda.libs.permissions.endpoints.v1.converter.convertToDto
import net.corda.libs.permissions.endpoints.v1.converter.convertToEndpointType
import net.corda.libs.permissions.endpoints.v1.permission.PermissionEndpoint
import net.corda.libs.permissions.endpoints.v1.permission.types.CreatePermissionType
import net.corda.libs.permissions.endpoints.v1.permission.types.PermissionResponseType
import net.corda.libs.permissions.manager.request.GetPermissionRequestDto
import net.corda.lifecycle.Lifecycle
import net.corda.lifecycle.LifecycleCoordinatorFactory
import net.corda.lifecycle.createCoordinator
import net.corda.permissions.service.PermissionServiceComponent
import org.osgi.service.component.annotations.Activate
import org.osgi.service.component.annotations.Component
import org.osgi.service.component.annotations.Reference

/**
 * An RPC Ops endpoint for Permission operations.
 */
@Component(service = [PluggableRPCOps::class])
class PermissionEndpointImpl @Activate constructor(
    @Reference(service = LifecycleCoordinatorFactory::class)
    private val coordinatorFactory: LifecycleCoordinatorFactory,
    @Reference(service = PermissionServiceComponent::class)
    private val permissionServiceComponent: PermissionServiceComponent
) : PermissionEndpoint, PluggableRPCOps<PermissionEndpoint>, Lifecycle {

    override val targetInterface: Class<PermissionEndpoint> = PermissionEndpoint::class.java

    override val protocolVersion = 1

    private val coordinator = coordinatorFactory.createCoordinator<PermissionEndpoint>(
        PermissionEndpointEventHandler("PermissionEndpoint")
    )

    override fun createPermission(createPermissionType: CreatePermissionType): PermissionResponseType {
        val rpcContext = CURRENT_RPC_CONTEXT.get()
        val principal = rpcContext.principal

        val createPermissionResult = withPermissionManager(permissionServiceComponent.permissionManager) {
            createPermission(createPermissionType.convertToDto(principal))
        }

        return createPermissionResult!!.convertToEndpointType()
    }

    override fun getPermission(id: String): PermissionResponseType {
        val rpcContext = CURRENT_RPC_CONTEXT.get()
        val principal = rpcContext.principal

        val permissionResponseDto = withPermissionManager(permissionServiceComponent.permissionManager) {
            getPermission(GetPermissionRequestDto(principal, id))
        }

        return permissionResponseDto?.convertToEndpointType() ?: throw ResourceNotFoundException("Permission", id)
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