package net.corda.libs.permissions.storage.writer.impl.role

import net.corda.data.permissions.management.role.CreateRoleRequest
import net.corda.data.permissions.Role as AvroRole

/**
 * Responsible for writing Role operations to data storage.
 */
interface RoleWriter {
    /**
     * Create and persist a Role entity and return its Avro representation.
     *
     * @param request CreateRoleRequest containing the information of the Role to create.
     * @param requestUserId ID of the user who made the request.
     */
    fun createRole(request: CreateRoleRequest, requestUserId: String): AvroRole
}