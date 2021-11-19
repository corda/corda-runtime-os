package net.corda.libs.permissions.storage.reader.impl

import net.corda.data.permissions.ChangeDetails as AvroChangeDetails
import net.corda.data.permissions.PermissionAssociation as AvroPermissionAssociation
import net.corda.data.permissions.Property as AvroProperty
import net.corda.data.permissions.RoleAssociation as AvroRoleAssociation
import net.corda.permissions.model.Group
import net.corda.permissions.model.PermissionType
import net.corda.permissions.model.Role
import net.corda.permissions.model.User
import net.corda.data.permissions.Group as AvroGroup
import net.corda.data.permissions.PermissionType as AvroPermissionType
import net.corda.data.permissions.Role as AvroRole
import net.corda.data.permissions.User as AvroUser
import net.corda.data.permissions.Permission as AvroPermission

fun User.toAvroUser(): AvroUser {
    return AvroUser(
        id,
        version,
        AvroChangeDetails(updateTimestamp),
        loginName,
        fullName,
        enabled,
        hashedPassword,
        saltValue,
        passwordExpiry,
        hashedPassword == null,
        parentGroup?.id,
        userProperties.map { property ->
            AvroProperty(
                property.id,
                property.version,
                AvroChangeDetails(property.updateTimestamp),
                property.key,
                property.value
            )
        },
        roleUserAssociations.map { roleUserAssociation ->
            AvroRoleAssociation(
                AvroChangeDetails(roleUserAssociation.updateTimestamp),
                roleUserAssociation.role.id
            )
        }
    )
}

fun Group.toAvroGroup(): AvroGroup {
    return AvroGroup(
        id,
        version,
        AvroChangeDetails(updateTimestamp),
        name,
        parentGroup?.id,
        groupProperties.map { property ->
            AvroProperty(
                property.id,
                property.version,
                AvroChangeDetails(property.updateTimestamp),
                property.key,
                property.value
            )
        },
        roleGroupAssociations.map { roleGroupAssociation ->
            AvroRoleAssociation(
                AvroChangeDetails(roleGroupAssociation.updateTimestamp),
                roleGroupAssociation.role.id
            )
        }
    )
}

fun Role.toAvroRole(): AvroRole {
    return AvroRole(
        id,
        version,
        AvroChangeDetails(updateTimestamp),
        name,
        rolePermAssociations.map { rolePermissionAssociation ->
            AvroPermissionAssociation(
                AvroChangeDetails(rolePermissionAssociation.updateTimestamp),
                AvroPermission(
                    rolePermissionAssociation.permission.id,
                    rolePermissionAssociation.permission.version,
                    AvroChangeDetails(rolePermissionAssociation.permission.updateTimestamp),
                    rolePermissionAssociation.permission.virtualNode,
                    rolePermissionAssociation.permission.permissionString,
                    rolePermissionAssociation.permission.permissionType.toAvroPermissionType()
                )
            )
        }
    )
}

fun PermissionType.toAvroPermissionType(): AvroPermissionType {
    return when (this) {
        PermissionType.ALLOW -> AvroPermissionType.ALLOW
        PermissionType.DENY -> AvroPermissionType.DENY
    }
}