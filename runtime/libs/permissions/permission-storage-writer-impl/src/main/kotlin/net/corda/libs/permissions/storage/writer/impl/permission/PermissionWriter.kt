package net.corda.libs.permissions.storage.writer.impl.permission

import net.corda.data.permissions.management.permission.CreatePermissionRequest
import net.corda.data.permissions.Permission as AvroPermission

/**
 * Responsible for writing Permission operations to data storage.
 */
interface PermissionWriter {

    /**
     * Create and persist a Permission entity and return its Avro representation.
     *
     * @param request CreatePermissionRequest containing the information of the Permission to create.
     * @param requestUserId ID of the user who made the request.
     * @param virtualNodeId optional ID of the virtual Node.
     */
    fun createPermission(request: CreatePermissionRequest, requestUserId: String, virtualNodeId: String?): AvroPermission
}