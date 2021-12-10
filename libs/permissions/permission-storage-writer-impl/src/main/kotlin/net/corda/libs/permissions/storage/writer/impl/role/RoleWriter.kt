package net.corda.libs.permissions.storage.writer.impl.role

import net.corda.data.permissions.management.role.CreateRoleRequest

interface RoleWriter {
    fun createRole(request: CreateRoleRequest): net.corda.data.permissions.Role
}