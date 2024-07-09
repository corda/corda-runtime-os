package net.corda.libs.permissions.endpoints.v1.group.impl

import net.corda.libs.permissions.endpoints.common.PermissionEndpointEventHandler
import net.corda.libs.permissions.endpoints.common.withPermissionManager
import net.corda.libs.permissions.endpoints.v1.converter.convertToDto
import net.corda.libs.permissions.endpoints.v1.converter.convertToEndpointType
import net.corda.libs.permissions.endpoints.v1.group.GroupEndpoint
import net.corda.libs.permissions.endpoints.v1.group.types.CreateGroupType
import net.corda.libs.permissions.endpoints.v1.group.types.GroupContentResponseType
import net.corda.libs.permissions.endpoints.v1.group.types.GroupResponseType
import net.corda.libs.permissions.manager.request.AddRoleToGroupRequestDto
import net.corda.libs.permissions.manager.request.ChangeGroupParentIdDto
import net.corda.libs.permissions.manager.request.DeleteGroupRequestDto
import net.corda.libs.permissions.manager.request.RemoveRoleFromGroupRequestDto
import net.corda.libs.platform.PlatformInfoProvider
import net.corda.lifecycle.Lifecycle
import net.corda.lifecycle.LifecycleCoordinatorFactory
import net.corda.lifecycle.createCoordinator
import net.corda.permissions.management.PermissionManagementService
import net.corda.rest.PluggableRestResource
import net.corda.rest.exception.ExceptionDetails
import net.corda.rest.exception.ResourceNotFoundException
import net.corda.rest.response.ResponseEntity
import net.corda.rest.security.CURRENT_REST_CONTEXT
import org.osgi.service.component.annotations.Activate
import org.osgi.service.component.annotations.Component
import org.osgi.service.component.annotations.Reference
import org.slf4j.Logger
import org.slf4j.LoggerFactory

@Component(service = [PluggableRestResource::class])
class GroupEndpointImpl @Activate constructor(
    @Reference(service = LifecycleCoordinatorFactory::class)
    private val coordinatorFactory: LifecycleCoordinatorFactory,
    @Reference(service = PermissionManagementService::class)
    private val permissionManagementService: PermissionManagementService,
    @Reference(service = PlatformInfoProvider::class)
    private val platformInfoProvider: PlatformInfoProvider
) : GroupEndpoint, PluggableRestResource<GroupEndpoint>, Lifecycle {

    private companion object {
        val logger: Logger = LoggerFactory.getLogger(this::class.java.enclosingClass)
    }

    override val targetInterface: Class<GroupEndpoint> = GroupEndpoint::class.java

    override val protocolVersion get() = platformInfoProvider.localWorkerPlatformVersion

    private val coordinator = coordinatorFactory.createCoordinator<GroupEndpoint>(
        PermissionEndpointEventHandler("GroupEndpoint")
    )

    override fun createGroup(createGroupType: CreateGroupType): ResponseEntity<GroupResponseType> {
        val principal = getRestThreadLocalContext()

        val createGroupResult = withPermissionManager(permissionManagementService.permissionManager, logger) {
            createGroup(createGroupType.convertToDto(principal))
        }

        return ResponseEntity.created(createGroupResult.convertToEndpointType())
    }

    override fun changeParentGroup(groupId: String, newParentGroupId: String?): ResponseEntity<GroupResponseType> {
        val principal = getRestThreadLocalContext()

        val groupResponseDto = withPermissionManager(permissionManagementService.permissionManager, logger) {
            try {
                changeParentGroup(ChangeGroupParentIdDto(principal, groupId, newParentGroupId))
            } catch (e: NoSuchElementException) {
                throw ResourceNotFoundException(
                    e::class.java.simpleName,
                    ExceptionDetails(e::class.java.name, e.message ?: "No resource found for this request.")
                )
            }
        }

        return ResponseEntity.updated(groupResponseDto.convertToEndpointType())
    }

    override fun addRole(groupId: String, roleId: String): ResponseEntity<GroupResponseType> {
        val principal = getRestThreadLocalContext()

        val result = withPermissionManager(permissionManagementService.permissionManager, logger) {
            addRoleToGroup(AddRoleToGroupRequestDto(principal, groupId, roleId))
        }
        return ResponseEntity.updated(result.convertToEndpointType())
    }

    override fun removeRole(groupId: String, roleId: String): ResponseEntity<GroupResponseType> {
        val principal = getRestThreadLocalContext()

        val result = withPermissionManager(permissionManagementService.permissionManager, logger) {
            removeRoleFromGroup(RemoveRoleFromGroupRequestDto(principal, groupId, roleId))
        }
        return ResponseEntity.updated(result.convertToEndpointType())
    }

    override fun getGroupContent(groupId: String): GroupContentResponseType {
        val groupContentResponseDto = withPermissionManager(permissionManagementService.permissionManager, logger) {
            getGroupContent(groupId)
        } ?: throw ResourceNotFoundException("Group", groupId)

        return groupContentResponseDto.convertToEndpointType()
    }

    override fun deleteGroup(groupId: String): ResponseEntity<GroupResponseType> {
        val principal = getRestThreadLocalContext()

        val groupResponseDto = withPermissionManager(permissionManagementService.permissionManager, logger) {
            deleteGroup(DeleteGroupRequestDto(principal, groupId))
        }

        return ResponseEntity.deleted(groupResponseDto.convertToEndpointType())
    }

    private fun getRestThreadLocalContext(): String {
        val restContext = CURRENT_REST_CONTEXT.get()
        return restContext.principal
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
