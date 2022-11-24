package net.corda.libs.permissions.endpoints.v1.permission.impl

import net.corda.httprpc.PluggableRPCOps
import net.corda.httprpc.exception.InvalidInputDataException
import net.corda.httprpc.exception.ResourceNotFoundException
import net.corda.httprpc.response.ResponseEntity
import net.corda.httprpc.security.CURRENT_RPC_CONTEXT
import net.corda.libs.permissions.endpoints.common.PermissionEndpointEventHandler
import net.corda.libs.permissions.endpoints.common.withPermissionManager
import net.corda.libs.permissions.endpoints.v1.converter.convertToDto
import net.corda.libs.permissions.endpoints.v1.converter.convertToEndpointType
import net.corda.libs.permissions.endpoints.v1.converter.toRequestDtoType
import net.corda.libs.permissions.endpoints.v1.permission.PermissionEndpoint
import net.corda.libs.permissions.endpoints.v1.permission.types.BulkCreatePermissionsRequestType
import net.corda.libs.permissions.endpoints.v1.permission.types.BulkCreatePermissionsResponseType
import net.corda.libs.permissions.endpoints.v1.permission.types.CreatePermissionType
import net.corda.libs.permissions.endpoints.v1.permission.types.PermissionResponseType
import net.corda.libs.permissions.endpoints.v1.permission.types.PermissionType
import net.corda.libs.permissions.manager.request.GetPermissionRequestDto
import net.corda.libs.permissions.manager.request.QueryPermissionsRequestDto
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
@Suppress("unused")
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

    override fun createPermission(createPermissionType: CreatePermissionType): ResponseEntity<PermissionResponseType> {
        val rpcContext = CURRENT_RPC_CONTEXT.get()
        val principal = rpcContext.principal

        val createPermissionResult = withPermissionManager(permissionManagementService.permissionManager, logger) {
            createPermission(createPermissionType.convertToDto(principal))
        }

        return ResponseEntity.created(createPermissionResult.convertToEndpointType())
    }

    override fun getPermission(id: String): PermissionResponseType {
        val rpcContext = CURRENT_RPC_CONTEXT.get()
        val principal = rpcContext.principal

        val permissionResponseDto = withPermissionManager(permissionManagementService.permissionManager, logger) {
            getPermission(GetPermissionRequestDto(principal, id))
        }

        return permissionResponseDto?.convertToEndpointType() ?: throw ResourceNotFoundException("Permission", id)
    }

    override fun queryPermissions(
        limit: Int,
        permissionType: String,
        groupVisibility: String?,
        virtualNode: String?,
        permissionStringPrefix: String?
    ): List<PermissionResponseType> {

        if (limit < 1 || limit > 1000) {
            throw InvalidInputDataException(
                "limit supplied $limit is outside of the permitted range of [1..1000]"
            )
        }

        val permissionTypeEnum = try {
            PermissionType.valueOf(permissionType)
        } catch (ex: Exception) {
            throw InvalidInputDataException(
                "permissionType: $permissionType is invalid. Supported values are: ${
                    PermissionType.values().map { it.name }
                }"
            )
        }

        val permissions = withPermissionManager(permissionManagementService.permissionManager, logger) {
            queryPermissions(
                QueryPermissionsRequestDto(
                    limit,
                    permissionTypeEnum.toRequestDtoType(),
                    groupVisibility,
                    virtualNode,
                    permissionStringPrefix
                )
            )
        }

        return permissions.map { it.convertToEndpointType() }
    }

    override fun createAndAssignPermissions(permissionsToCreate: BulkCreatePermissionsRequestType):
            ResponseEntity<BulkCreatePermissionsResponseType> {

        // Validate non-empty set of permissions requested
        if(permissionsToCreate.permissionsToCreate.isEmpty()) {
            throw InvalidInputDataException("No permissions requested to be created")
        }

        // Validate RoleIds passed in
        if (permissionsToCreate.roleIds.isNotEmpty()) {
            val allRoleIds = permissionManagementService.permissionManager.getRoles().map { it.id }
            val intersection = allRoleIds.intersect(permissionsToCreate.roleIds)
            if (intersection != permissionsToCreate.roleIds) {
                val notFoundRoles = permissionsToCreate.roleIds.subtract(intersection)
                throw InvalidInputDataException("Roles with the following ids cannot be found: $notFoundRoles")
            }
        }

        val rpcContext = CURRENT_RPC_CONTEXT.get()
        val principal = rpcContext.principal

        // Construct and send Kafka message and wait for the response
        val createPermissionsResult = withPermissionManager(permissionManagementService.permissionManager, logger) {
            createPermissions(permissionsToCreate.convertToDto(principal))
        }

        return ResponseEntity.created(createPermissionsResult.convertToEndpointType())
    }

    override val isRunning: Boolean
        get() = coordinator.isRunning

    override fun start() {
        coordinator.start()
    }

    override fun stop() {
        coordinator.stop()
    }
}