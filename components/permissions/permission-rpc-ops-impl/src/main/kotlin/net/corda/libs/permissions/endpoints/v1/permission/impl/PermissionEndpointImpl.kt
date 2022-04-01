package net.corda.libs.permissions.endpoints.v1.permission.impl

import net.corda.httprpc.PluggableRPCOps
import net.corda.httprpc.exception.ResourceNotFoundException
import net.corda.httprpc.security.CURRENT_RPC_CONTEXT
import net.corda.libs.permissions.endpoints.common.PermissionEndpointEventHandler
import net.corda.libs.permissions.endpoints.common.withPermissionManager
import net.corda.libs.permissions.endpoints.v1.converter.convertToDto
import net.corda.libs.permissions.endpoints.v1.converter.convertToEndpointType
import net.corda.libs.permissions.endpoints.v1.permission.PermissionEndpoint
import net.corda.libs.permissions.endpoints.v1.permission.types.CreatePermissionType
import net.corda.libs.permissions.endpoints.v1.permission.types.PermissionResponseType
import net.corda.libs.permissions.manager.request.GetPermissionRequestDto
import net.corda.lifecycle.Lifecycle
import net.corda.lifecycle.LifecycleCoordinatorFactory
import net.corda.lifecycle.createCoordinator
import net.corda.permissions.management.PermissionManagementService
import net.corda.v5.base.util.contextLogger
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
    @Reference(service = PermissionManagementService::class)
    private val permissionManagementService: PermissionManagementService,
) : PermissionEndpoint, PluggableRPCOps<PermissionEndpoint>, Lifecycle {

    private companion object {
        val logger = contextLogger()
    }

    override val targetInterface: Class<PermissionEndpoint> = PermissionEndpoint::class.java

    override val protocolVersion = 1

    private val coordinator = coordinatorFactory.createCoordinator<PermissionEndpoint>(
        PermissionEndpointEventHandler("PermissionEndpoint")
    )

    override fun createPermission(createPermissionType: CreatePermissionType): PermissionResponseType {
        val rpcContext = CURRENT_RPC_CONTEXT.get()
        val principal = rpcContext.principal

        val createPermissionResult = withPermissionManager(permissionManagementService.permissionManager, logger) {
            createPermission(createPermissionType.convertToDto(principal))
        }

        return createPermissionResult!!.convertToEndpointType()
    }

    override fun getPermission(id: String): PermissionResponseType {
        val rpcContext = CURRENT_RPC_CONTEXT.get()
        val principal = rpcContext.principal

        val permissionResponseDto = withPermissionManager(permissionManagementService.permissionManager, logger) {
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