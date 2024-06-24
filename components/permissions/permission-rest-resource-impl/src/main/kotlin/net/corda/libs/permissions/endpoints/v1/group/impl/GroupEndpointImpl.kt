package net.corda.libs.permissions.endpoints.v1.group.impl

import net.corda.libs.permissions.endpoints.common.withPermissionManager
import net.corda.libs.permissions.endpoints.v1.converter.convertToDto
import net.corda.libs.permissions.endpoints.v1.converter.convertToEndpointType
import net.corda.libs.permissions.endpoints.v1.group.GroupEndpoint
import net.corda.libs.permissions.endpoints.v1.group.types.CreateGroupType
import net.corda.libs.permissions.endpoints.v1.group.types.GroupResponseType
import net.corda.libs.permissions.endpoints.v1.group.types.GroupContentResponseType
import net.corda.libs.platform.PlatformInfoProvider
import net.corda.permissions.management.PermissionManagementService
import net.corda.rest.PluggableRestResource
import net.corda.rest.exception.ExceptionDetails
import net.corda.rest.exception.InvalidInputDataException
import net.corda.rest.response.ResponseEntity
import net.corda.rest.security.CURRENT_REST_CONTEXT
import org.osgi.service.component.annotations.Activate
import org.osgi.service.component.annotations.Component
import org.osgi.service.component.annotations.Reference
import org.slf4j.Logger
import org.slf4j.LoggerFactory

@Component(service = [PluggableRestResource::class])
class GroupEndpointImpl @Activate constructor(
    @Reference(service = PermissionManagementService::class)
    private val permissionManagementService: PermissionManagementService,
    @Reference(service = PlatformInfoProvider::class)
    private val platformInfoProvider: PlatformInfoProvider
) : GroupEndpoint {

    private companion object {
        val logger: Logger = LoggerFactory.getLogger(this::class.java.enclosingClass)

        private const val INVALID_ARGUMENT_MESSAGE = "Invalid argument in request."
    }

    override val protocolVersion get() = platformInfoProvider.localWorkerPlatformVersion

    override fun createGroup(createGroupType: CreateGroupType): ResponseEntity<GroupResponseType> {
        val principal = getRestThreadLocalContext()

        val createGroupResult = try {
            withPermissionManager(permissionManagementService.permissionManager, logger) {
                createGroup(createGroupType.convertToDto(principal))
            }
        } catch (e: IllegalArgumentException) {
            throw InvalidInputDataException(
                title = e::class.java.simpleName,
                exceptionDetails = ExceptionDetails(e::class.java.name, e.message ?: INVALID_ARGUMENT_MESSAGE)
            )
        }

        return ResponseEntity.created(createGroupResult.convertToEndpointType())
    }

    override fun changeParentGroup(groupId: String, newParentGroupId: String): ResponseEntity<GroupResponseType> {
        // Implement the logic to change the parent group ID for a group
        // For example, you might use a service to update the parent group ID of the specified group in your database
        // Then, convert the updated group to a GroupResponseType and return it
        throw NotImplementedError("Not implemented yet")
    }

    override fun assignRoleToGroup(groupId: String, roleId: String): ResponseEntity<GroupResponseType> {
        // Implement the logic to assign a role to a group
        // For example, you might use a service to assign the specified role to the specified group in your database
        // Then, convert the updated group to a GroupResponseType and return it
        throw NotImplementedError("Not implemented yet")
    }

    override fun unassignRoleFromGroup(groupId: String, roleId: String): ResponseEntity<GroupResponseType> {
        // Implement the logic to unassign a role from a group
        // For example, you might use a service to unassign the specified role from the specified group in your database
        // Then, convert the updated group to a GroupResponseType and return it
        throw NotImplementedError("Not implemented yet")
    }

    override fun getGroupContent(groupId: String): GroupContentResponseType {
        // Implement the logic to get the content of a group
        // For example, you might use a service to retrieve the specified group from your database
        // Then, convert the group to a GroupContentResponseType and return it
        throw NotImplementedError("Not implemented yet")
    }

    override fun deleteGroup(groupId: String): ResponseEntity<GroupResponseType> {
        // Implement the logic to delete a group if it is empty
        // For example, you might use a service to delete the specified group from your database if it is empty
        // Then, convert the deleted group to a GroupResponseType and return it
        throw NotImplementedError("Not implemented yet")
    }

    private fun getRestThreadLocalContext(): String {
        val restContext = CURRENT_REST_CONTEXT.get()
        return restContext.principal
    }
}