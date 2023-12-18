package net.corda.libs.permissions.endpoints.v1.role.impl

import net.corda.libs.permissions.common.constant.RoleKeys.DEFAULT_SYSTEM_ADMIN_ROLE
import net.corda.libs.permissions.endpoints.common.PermissionEndpointEventHandler
import net.corda.libs.permissions.endpoints.common.withPermissionManager
import net.corda.libs.permissions.endpoints.v1.RoleUtils.initialAdminRole
import net.corda.libs.permissions.endpoints.v1.converter.convertToDto
import net.corda.libs.permissions.endpoints.v1.converter.convertToEndpointType
import net.corda.libs.permissions.endpoints.v1.role.RoleEndpoint
import net.corda.libs.permissions.endpoints.v1.role.types.CreateRoleType
import net.corda.libs.permissions.endpoints.v1.role.types.RoleResponseType
import net.corda.libs.permissions.manager.PermissionManager
import net.corda.libs.permissions.manager.request.GetRoleRequestDto
import net.corda.libs.platform.PlatformInfoProvider
import net.corda.lifecycle.Lifecycle
import net.corda.lifecycle.LifecycleCoordinatorFactory
import net.corda.lifecycle.createCoordinator
import net.corda.permissions.management.PermissionManagementService
import net.corda.rest.PluggableRestResource
import net.corda.rest.exception.BadRequestException
import net.corda.rest.exception.ResourceNotFoundException
import net.corda.rest.response.ResponseEntity
import net.corda.rest.security.CURRENT_REST_CONTEXT
import org.osgi.service.component.annotations.Activate
import org.osgi.service.component.annotations.Component
import org.osgi.service.component.annotations.Reference
import org.slf4j.Logger
import org.slf4j.LoggerFactory

/**
 * A REST resource endpoint for Role operations.
 */
@Component(service = [PluggableRestResource::class])
class RoleEndpointImpl @Activate constructor(
    @Reference(service = LifecycleCoordinatorFactory::class)
    private val coordinatorFactory: LifecycleCoordinatorFactory,
    @Reference(service = PermissionManagementService::class)
    private val permissionManagementService: PermissionManagementService,
    @Reference(service = PlatformInfoProvider::class)
    private val platformInfoProvider: PlatformInfoProvider
) : RoleEndpoint, PluggableRestResource<RoleEndpoint>, Lifecycle {

    private companion object {
        val logger: Logger = LoggerFactory.getLogger(this::class.java.enclosingClass)

        @Suppress("ThrowsCount")
        private fun PermissionManager.checkProtectedRole(roleId: String, principal: String) {
            val role = getRole(GetRoleRequestDto(principal, roleId))
                ?: throw ResourceNotFoundException("Role ID", roleId)

            if (role.initialAdminRole) {
                throw BadRequestException("$DEFAULT_SYSTEM_ADMIN_ROLE cannot be changed", mapOf("roleId" to roleId))
            }
        }
    }

    override val targetInterface: Class<RoleEndpoint> = RoleEndpoint::class.java

    override val protocolVersion get() = platformInfoProvider.localWorkerPlatformVersion

    private val coordinator = coordinatorFactory.createCoordinator<RoleEndpoint>(
        PermissionEndpointEventHandler("RoleEndpoint")
    )

    override fun getRoles(): Set<RoleResponseType> {
        val allRoles = withPermissionManager(permissionManagementService.permissionManager, logger) {
            getRoles()
        }
        return allRoles.map { it.convertToEndpointType() }.toSet()
    }

    override fun createRole(createRoleType: CreateRoleType): ResponseEntity<RoleResponseType> {
        val restContext = CURRENT_REST_CONTEXT.get()
        val principal = restContext.principal

        val createRoleResult = withPermissionManager(permissionManagementService.permissionManager, logger) {
            createRole(createRoleType.convertToDto(principal))
        }

        return ResponseEntity.created(createRoleResult.convertToEndpointType())
    }

    override fun getRole(id: String): RoleResponseType {
        val restContext = CURRENT_REST_CONTEXT.get()
        val principal = restContext.principal

        val roleResponseDto = withPermissionManager(permissionManagementService.permissionManager, logger) {
            getRole(GetRoleRequestDto(principal, id))
        }

        return roleResponseDto?.convertToEndpointType() ?: throw ResourceNotFoundException("Role", id)
    }

    override fun addPermission(roleId: String, permissionId: String): ResponseEntity<RoleResponseType> {
        val restContext = CURRENT_REST_CONTEXT.get()
        val principal = restContext.principal

        val updatedRoleResult = withPermissionManager(permissionManagementService.permissionManager, logger) {
            addPermissionToRole(roleId, permissionId, principal)
        }

        return ResponseEntity.updated(updatedRoleResult.convertToEndpointType())
    }

    override fun removePermission(roleId: String, permissionId: String): ResponseEntity<RoleResponseType> {
        val restContext = CURRENT_REST_CONTEXT.get()
        val principal = restContext.principal

        val updatedRoleResult = withPermissionManager(permissionManagementService.permissionManager, logger) {
            checkProtectedRole(roleId, principal)
            removePermissionFromRole(roleId, permissionId, principal)
        }

        return ResponseEntity.deleted(updatedRoleResult.convertToEndpointType())
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
