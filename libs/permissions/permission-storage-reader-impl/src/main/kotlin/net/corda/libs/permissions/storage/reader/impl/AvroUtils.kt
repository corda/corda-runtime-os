package net.corda.libs.permissions.storage.reader.impl

import net.corda.data.permissions.ChangeDetails
import net.corda.data.permissions.Property
import net.corda.permissions.model.Group
import net.corda.permissions.model.PermissionType
import net.corda.permissions.model.Role
import net.corda.permissions.model.User
import net.corda.data.permissions.Group as AvroGroup
import net.corda.data.permissions.PermissionType as AvroPermissionType
import net.corda.data.permissions.Role as AvroRole
import net.corda.data.permissions.User as AvroUser

fun User.toAvroUser(): AvroUser {
    return AvroUser(
        id,
        version,
        ChangeDetails(updateTimestamp, "Need to get the changed by user from somewhere"),
        fullName,
        enabled,
        hashedPassword,
        saltValue,
        hashedPassword == null,
        parentGroup?.id,
        userProperties.map { property ->
            Property(
                property.id,
                property.version,
                ChangeDetails(property.updateTimestamp, "Need to get the changed by user from somewhere"),
                property.key,
                property.value
            )
        },
        roleUserAssociations.map { it.role.id }
    )
}

fun Group.toAvroGroup(): AvroGroup {
    return AvroGroup(
        id,
        version,
        ChangeDetails(updateTimestamp, "Need to get the changed by user from somewhere"),
        name,
        parentGroup?.id,
        groupProperties.map { property ->
            Property(
                property.id,
                property.version,
                ChangeDetails(property.updateTimestamp, "Need to get the changed by user from somewhere"),
                property.key,
                property.value
            )
        },
        roleGroupAssociations.map { it.role.id }
    )
}

fun Role.toAvroRole(): AvroRole {
    return AvroRole(
        id,
        version,
        ChangeDetails(updateTimestamp, "Need to get the changed by user from somewhere"),
        name,
        rolePermAssociations.map { it.permission }.map { permission ->
            net.corda.data.permissions.Permission(
                permission.id,
                permission.version,
                ChangeDetails(permission.updateTimestamp, "Need to get the changed by user from somewhere"),
                permission.virtualNode,
                permission.permissionString,
                permission.permissionType.toAvroPermissionType()
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