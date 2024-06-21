package net.corda.libs.permissions.endpoints.v1.group.impl

import net.corda.libs.permissions.endpoints.v1.group.GroupEndpoint
import net.corda.libs.permissions.endpoints.v1.group.types.CreateGroupType
import net.corda.libs.permissions.endpoints.v1.group.types.GroupResponseType
import net.corda.libs.permissions.endpoints.v1.group.types.GroupContentResponseType
import net.corda.libs.platform.PlatformInfoProvider
import net.corda.rest.PluggableRestResource
import net.corda.rest.response.ResponseEntity
import org.osgi.service.component.annotations.Activate
import org.osgi.service.component.annotations.Component
import org.osgi.service.component.annotations.Reference

@Component(service = [PluggableRestResource::class])
class GroupEndpointImpl @Activate constructor(
    @Reference(service = PlatformInfoProvider::class)
    private val platformInfoProvider: PlatformInfoProvider
) : GroupEndpoint {

    override val protocolVersion get() = platformInfoProvider.localWorkerPlatformVersion

    override fun createGroup(createGroupType: CreateGroupType): ResponseEntity<GroupResponseType> {
        // Implement the logic to create a group
        // For example, you might use a service to create a group in your database
        // Then, convert the created group to a GroupResponseType and return it
        throw NotImplementedError("Not implemented yet")
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
}