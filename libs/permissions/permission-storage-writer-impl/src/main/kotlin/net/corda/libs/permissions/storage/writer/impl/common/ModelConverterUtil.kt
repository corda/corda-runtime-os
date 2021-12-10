package net.corda.libs.permissions.storage.writer.impl.common

import net.corda.data.permissions.ChangeDetails
import net.corda.data.permissions.PermissionAssociation
import net.corda.data.permissions.Property
import net.corda.data.permissions.RoleAssociation
import net.corda.permissions.model.Permission
import net.corda.permissions.model.PermissionType
import net.corda.permissions.model.Role
import net.corda.permissions.model.RolePermissionAssociation
import net.corda.permissions.model.RoleUserAssociation
import net.corda.permissions.model.User
import net.corda.permissions.model.UserProperty

/**
 * This Utility class contains functions for converting from model objects to avro objects to be placed on the messaging bus. For example:
 */

/**
 * Convert from model User entity to Avro User object.
 */
fun User.toAvroUser(): net.corda.data.permissions.User {
    return net.corda.data.permissions.User(
        id,
        version,
        ChangeDetails(updateTimestamp),
        loginName,
        fullName,
        enabled,
        hashedPassword,
        saltValue,
        passwordExpiry,
        false,
        parentGroup?.id,
        userProperties.map { it.toAvroUserProperty() },
        roleUserAssociations.map { it.toAvroRoleAssociation() }
    )
}

/**
 * Convert from model RoleUserAssociation entity to Avro Role Association object.
 */
fun RoleUserAssociation.toAvroRoleAssociation() : RoleAssociation {
    return RoleAssociation(
        ChangeDetails(updateTimestamp),
        role.id
    )
}

/**
 * Convert from model UserProperty entity to Avro Property object.
 */
fun UserProperty.toAvroUserProperty(): Property {
    return Property(
        id,
        version,
        ChangeDetails(updateTimestamp),
        key,
        value
    )
}

/**
 * Convert from model Role entity to Avro Role object.
 */
fun Role.toAvroRole(): net.corda.data.permissions.Role {
    return net.corda.data.permissions.Role(
        id,
        version,
        ChangeDetails(updateTimestamp),
        name,
        groupVisibility?.id,
        rolePermAssociations.map { it.toAvroPermissionAssociation() }
    )
}

/**
 * Convert from model RolePermissionAssociation entity to Avro PermissionAssociation object.
 */
fun RolePermissionAssociation.toAvroPermissionAssociation(): PermissionAssociation {
    return PermissionAssociation(
        ChangeDetails(updateTimestamp),
        permission.toAvroPermission()
    )
}

/**
 * Convert from model Permission entity to Avro Permission object.
 */
fun Permission.toAvroPermission(): net.corda.data.permissions.Permission {
    return net.corda.data.permissions.Permission(
        id,
        version,
        ChangeDetails(updateTimestamp),
        virtualNode,
        permissionString,
        groupVisibility?.id,
        permissionType.toAvroPermissionType()
    )
}

/**
 * Convert from model PermissionType to Avro PermissionType.
 */
fun PermissionType.toAvroPermissionType(): net.corda.data.permissions.PermissionType {
    return net.corda.data.permissions.PermissionType.valueOf(name)
}