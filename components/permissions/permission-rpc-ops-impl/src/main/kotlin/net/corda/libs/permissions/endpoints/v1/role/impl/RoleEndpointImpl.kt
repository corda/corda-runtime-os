package net.corda.libs.permissions.endpoints.v1.role.impl

import net.corda.httprpc.PluggableRPCOps
import net.corda.httprpc.exception.ResourceNotFoundException
import net.corda.httprpc.security.CURRENT_RPC_CONTEXT
import net.corda.libs.permissions.endpoints.common.PermissionEndpointEventHandler
import net.corda.libs.permissions.endpoints.common.withPermissionManager
import net.corda.libs.permissions.endpoints.v1.converter.convertToDto
import net.corda.libs.permissions.endpoints.v1.converter.convertToEndpointType
import net.corda.libs.permissions.endpoints.v1.role.RoleEndpoint
import net.corda.libs.permissions.endpoints.v1.role.types.CreateRoleType
import net.corda.libs.permissions.endpoints.v1.role.types.RoleResponseType
import net.corda.libs.permissions.manager.request.GetRoleRequestDto
import net.corda.lifecycle.Lifecycle
import net.corda.lifecycle.LifecycleCoordinatorFactory
import net.corda.lifecycle.createCoordinator
import net.corda.permissions.management.PermissionManagementService
import net.corda.v5.base.util.contextLogger
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
    @Reference(service = PermissionManagementService::class)
    private val permissionManagementService: PermissionManagementService
) : RoleEndpoint, PluggableRPCOps<RoleEndpoint>, Lifecycle {

    private companion object {
        val logger = contextLogger()
    }

    override val targetInterface: Class<RoleEndpoint> = RoleEndpoint::class.java

    override val protocolVersion = 1

    private val coordinator = coordinatorFactory.createCoordinator<RoleEndpoint>(
        PermissionEndpointEventHandler("RoleEndpoint")
    )

    override fun createRole(createRoleType: CreateRoleType): RoleResponseType {
        val rpcContext = CURRENT_RPC_CONTEXT.get()
        val principal = rpcContext.principal

        val createRoleResult = withPermissionManager(permissionManagementService.permissionManager, logger) {
            createRole(createRoleType.convertToDto(principal))
        }

        return createRoleResult!!.convertToEndpointType()
    }

    override fun getRole(id: String): RoleResponseType {
        val rpcContext = CURRENT_RPC_CONTEXT.get()
        val principal = rpcContext.principal

        val roleResponseDto = withPermissionManager(permissionManagementService.permissionManager, logger) {
            getRole(GetRoleRequestDto(principal, id))
        }

        return roleResponseDto?.convertToEndpointType() ?: throw ResourceNotFoundException("Role", id)
    }

    override fun addPermission(roleId: String, permissionId: String): RoleResponseType {
        val rpcContext = CURRENT_RPC_CONTEXT.get()
        val principal = rpcContext.principal

        val updatedRoleResult = withPermissionManager(permissionManagementService.permissionManager, logger) {
            addPermissionToRole(roleId, permissionId, principal)
        }

        return updatedRoleResult!!.convertToEndpointType()
    }

    override fun removePermission(roleId: String, permissionId: String): RoleResponseType {
        val rpcContext = CURRENT_RPC_CONTEXT.get()
        val principal = rpcContext.principal

        val updatedRoleResult = withPermissionManager(permissionManagementService.permissionManager, logger) {
            removePermissionFromRole(roleId, permissionId, principal)
        }

        return updatedRoleResult!!.convertToEndpointType()
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