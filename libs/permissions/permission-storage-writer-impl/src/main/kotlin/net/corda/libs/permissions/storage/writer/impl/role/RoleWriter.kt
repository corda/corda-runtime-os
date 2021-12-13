package net.corda.libs.permissions.storage.writer.impl.role

import net.corda.data.permissions.management.role.CreateRoleRequest
import net.corda.data.permissions.Role as AvroRole

interface RoleWriter {
    fun createRole(request: CreateRoleRequest, requestUserId: String): AvroRole
}