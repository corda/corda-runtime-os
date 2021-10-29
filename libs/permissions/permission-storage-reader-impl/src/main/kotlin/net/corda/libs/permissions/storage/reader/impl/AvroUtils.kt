package net.corda.libs.permissions.storage.reader.impl

import net.corda.data.permissions.ChangeDetails
import net.corda.data.permissions.Property
import net.corda.permissions.model.Group
import net.corda.permissions.model.PermissionType
import net.corda.permissions.model.Role
import net.corda.permissions.model.User

fun User.toAvroUser(): net.corda.data.permissions.User {
    return net.corda.data.permissions.User(
        id,
        version,
        ChangeDetails(updateTimestamp, "Need to get the changed by user from somewhere"),
        fullName,
        enabled,
        hashedPassword,
        saltValue,
        false, // ssoAuth isn't in the db
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

fun Group.toAvroGroup(): net.corda.data.permissions.Group {
    return net.corda.data.permissions.Group(
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

fun Role.toAvroRole(): net.corda.data.permissions.Role {
    return net.corda.data.permissions.Role(
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

fun PermissionType.toAvroPermissionType(): net.corda.data.permissions.PermissionType {
    return when (this) {
        PermissionType.ALLOW -> net.corda.data.permissions.PermissionType.ALLOW
        PermissionType.DENY -> net.corda.data.permissions.PermissionType.DENY
    }
}