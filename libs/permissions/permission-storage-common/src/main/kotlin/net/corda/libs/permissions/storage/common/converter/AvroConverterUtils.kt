package net.corda.libs.permissions.storage.common.converter

import net.corda.permissions.model.Group
import net.corda.permissions.model.GroupProperty
import net.corda.permissions.model.Permission
import net.corda.permissions.model.PermissionType
import net.corda.permissions.model.Role
import net.corda.permissions.model.RoleGroupAssociation
import net.corda.permissions.model.RolePermissionAssociation
import net.corda.permissions.model.RoleUserAssociation
import net.corda.permissions.model.User
import net.corda.permissions.model.UserProperty
import net.corda.data.permissions.ChangeDetails as AvroChangeDetails
import net.corda.data.permissions.Permission as AvroPermission
import net.corda.data.permissions.PermissionAssociation as AvroPermissionAssociation
import net.corda.data.permissions.PermissionType as AvroPermissionType
import net.corda.data.permissions.Property as AvroProperty
import net.corda.data.permissions.Role as AvroRole
import net.corda.data.permissions.RoleAssociation as AvroRoleAssociation
import net.corda.data.permissions.User as AvroUser
import net.corda.data.permissions.Group as AvroGroup

/**
 * This Utility class contains functions for converting from model objects to avro objects to be placed on the messaging bus. For example:
 */

/**
 * Convert from model User entity to Avro User object.
 */
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
        userProperties.map { it.toAvroUserProperty() },
        roleUserAssociations.map { it.toAvroRoleAssociation() }
    )
}

/**
 * Convert from model RoleUserAssociation entity to Avro Role Association object.
 */
fun RoleUserAssociation.toAvroRoleAssociation(): AvroRoleAssociation {
    return AvroRoleAssociation(
        AvroChangeDetails(updateTimestamp),
        role.id
    )
}

/**
 * Convert from model UserProperty entity to Avro Property object.
 */
fun UserProperty.toAvroUserProperty(): AvroProperty {
    return AvroProperty(
        id,
        version,
        AvroChangeDetails(updateTimestamp),
        key,
        value
    )
}

/**
 * Convert from model Role entity to Avro Role object.
 */
fun Role.toAvroRole(): AvroRole {
    return AvroRole(
        id,
        version,
        AvroChangeDetails(updateTimestamp),
        name,
        groupVisibility?.id,
        rolePermAssociations.map { it.toAvroPermissionAssociation() }
    )
}

/**
 * Convert from model RolePermissionAssociation entity to Avro PermissionAssociation object.
 */
fun RolePermissionAssociation.toAvroPermissionAssociation(): AvroPermissionAssociation {
    return AvroPermissionAssociation(
        AvroChangeDetails(updateTimestamp),
        permission.toAvroPermission()
    )
}

/**
 * Convert from model Permission entity to Avro Permission object.
 */
fun Permission.toAvroPermission(): AvroPermission {
    return AvroPermission(
        id,
        version,
        AvroChangeDetails(updateTimestamp),
        virtualNode,
        permissionType.toAvroPermissionType(),
        permissionString,
        groupVisibility?.id
    )
}

/**
 * Convert from model PermissionType to Avro PermissionType.
 */
fun PermissionType.toAvroPermissionType(): AvroPermissionType {
    return when(this) {
        PermissionType.ALLOW -> AvroPermissionType.ALLOW
        PermissionType.DENY -> AvroPermissionType.DENY
    }
}

fun AvroPermissionType.toDbModelPermissionType(): PermissionType  {
    return when(this) {
        AvroPermissionType.ALLOW -> PermissionType.ALLOW
        AvroPermissionType.DENY -> PermissionType.DENY
    }
}

/**
 * Convert from model Group to Avro Group.
 */
fun Group.toAvroGroup(): AvroGroup {
    return AvroGroup(
        id,
        version,
        AvroChangeDetails(updateTimestamp),
        name,
        parentGroup?.id,
        groupProperties.map { it.toAvroProperty() },
        roleGroupAssociations.map { it.toAvroRoleAssociation() }
    )
}

/**
 * Convert from model GroupProperty to Avro Property.
 */
fun GroupProperty.toAvroProperty(): AvroProperty {
    return AvroProperty(
        id,
        version,
        AvroChangeDetails(updateTimestamp),
        key,
        value
    )
}

/**
 * Convert from model RoleGroupAssociation to Avro RoleAssociation.
 */
fun RoleGroupAssociation.toAvroRoleAssociation(): AvroRoleAssociation {
    return AvroRoleAssociation(
        AvroChangeDetails(updateTimestamp),
        role.id
    )
}