package net.corda.libs.permissions.storage.writer.impl.group

import net.corda.data.permissions.management.group.AddRoleToGroupRequest
import net.corda.data.permissions.management.group.ChangeGroupParentIdRequest
import net.corda.data.permissions.management.group.CreateGroupRequest
import net.corda.data.permissions.management.group.DeleteGroupRequest
import net.corda.data.permissions.management.group.RemoveRoleFromGroupRequest
import net.corda.data.permissions.Group as AvroGroup

/**
 * Interface for writing group operations to data storage.
 */
interface GroupWriter {
    /**
     * Create and persist a Group entity and return its Avro representation.
     *
     * @param request CreateGroupRequest containing the information of the Group to create.
     * @param requestUserId ID of the user who made the request.
     */
    fun createGroup(request: CreateGroupRequest, requestUserId: String): AvroGroup

    /**
     * Change the parent group of a Group entity and return its Avro representation.
     *
     * @param request ChangeGroupParentIdRequest containing the information of the Group to change.
     * @param requestUserId ID of the user who made the request.
     */
    fun changeParentGroup(request: ChangeGroupParentIdRequest, requestUserId: String): AvroGroup

    /**
     * Associate a Role to a Group and return its Avro representation.
     *
     * @param request AddRoleToGroupRequest containing the information of the Role and Group to associate.
     * @param requestUserId ID of the user who made the request.
     */
    fun addRoleToGroup(request: AddRoleToGroupRequest, requestUserId: String): AvroGroup

    /**
     * Dissociate a Role from a Group and return its Avro representation.
     *
     * @param request RemoveRoleFromGroupRequest containing the information of the Role and Group to dissociate.
     * @param requestUserId ID of the user who made the request.
     */
    fun removeRoleFromGroup(request: RemoveRoleFromGroupRequest, requestUserId: String): AvroGroup

    /**
     * Delete a Group entity and return its Avro representation.
     *
     * @param request DeleteGroupRequest containing the information of the Group to delete.
     * @param requestUserId ID of the user who made the request.
     */
    fun deleteGroup(request: DeleteGroupRequest, requestUserId: String): AvroGroup
}
