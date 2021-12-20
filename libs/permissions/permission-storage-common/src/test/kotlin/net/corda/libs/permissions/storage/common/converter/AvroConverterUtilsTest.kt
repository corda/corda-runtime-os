package net.corda.libs.permissions.storage.common.converter

import java.time.Instant
import java.time.temporal.ChronoUnit
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
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import net.corda.data.permissions.PermissionType as AvroPermissionType

internal class AvroConverterUtilsTest {

    @Test
    fun `convert User to Avro User`() {
        val now = Instant.now().truncatedTo(ChronoUnit.MILLIS)

        val user = User(
            "userId1",
            now,
            "conal smith",
            "login1",
            true,
            "salt1",
            "pass1",
            now,
            null
        )
        val role = Role(
            "role1",
            now,
            "roleName1",
            null,
        )
        user.version = 0
        user.userProperties.add(UserProperty(
            "prop1",
            now,
            user,
            "key1",
            "val1"
        ))
        user.roleUserAssociations.add(RoleUserAssociation(
            "rua1",
            role,
            user,
            now
        ))

        val avroUser = user.toAvroUser()

        assertEquals("userId1", avroUser.id)
        assertEquals(now, avroUser.lastChangeDetails.updateTimestamp)
        assertEquals("conal smith", avroUser.fullName)
        assertEquals("login1", avroUser.loginName)
        assertTrue(avroUser.enabled)
        assertEquals("salt1", avroUser.saltValue)
        assertEquals("pass1", avroUser.hashedPassword)
        assertEquals(now, avroUser.passwordExpiry)
        assertNull(avroUser.parentGroupId)

        assertEquals(1, avroUser.properties.size)
        assertEquals("prop1", avroUser.properties[0].id)
        assertEquals(now, avroUser.properties[0].lastChangeDetails.updateTimestamp)
        assertEquals("key1", avroUser.properties[0].key)
        assertEquals("val1", avroUser.properties[0].value)

        assertEquals(1, avroUser.roleAssociations.size)
        assertEquals("role1", avroUser.roleAssociations[0].roleId)
        assertEquals(now, avroUser.roleAssociations[0].changeDetails.updateTimestamp)
    }

    @Test
    fun `convert Role to avro Role`() {
        val now = Instant.now().truncatedTo(ChronoUnit.MILLIS)
        val change = Instant.now().truncatedTo(ChronoUnit.MILLIS)
        val later = Instant.now().truncatedTo(ChronoUnit.MILLIS)

        val role = Role(
            "role1",
            now,
            "roleName1",
            null,
        )
        val permission = Permission(
            "perm1",
            later,
            null,
            "virtNode3",
            PermissionType.ALLOW,
            "*"
        )
        val permission2 = Permission(
            "perm2",
            later,
            null,
            "virtNode4",
            PermissionType.DENY,
            "*"
        )
        role.rolePermAssociations.add(
            RolePermissionAssociation(
                "rpa1", role, permission, change
            )
        )
        role.rolePermAssociations.add(
            RolePermissionAssociation(
                "rpa2", role, permission2, change
            )
        )

        val avroRole = role.toAvroRole()

        assertEquals("role1", avroRole.id)
        assertEquals(now, avroRole.lastChangeDetails.updateTimestamp)
        assertEquals("roleName1", avroRole.name)
        assertNull(avroRole.groupVisibility)

        assertEquals(2, avroRole.permissions.size)
        val permissionAssoc1 = avroRole.permissions[0]
        assertEquals(change, permissionAssoc1.changeDetails.updateTimestamp)

        val convertedPermission1 = permissionAssoc1.permission
        assertEquals("perm1", convertedPermission1.id)
        assertEquals(later, convertedPermission1.lastChangeDetails.updateTimestamp)
        assertNull(convertedPermission1.groupVisibility)
        assertEquals("virtNode3", convertedPermission1.virtualNode)
        assertEquals(AvroPermissionType.ALLOW, convertedPermission1.type)
        assertEquals("*", convertedPermission1.permissionString)

        val permissionAssoc2 = avroRole.permissions[1]
        assertEquals(change, permissionAssoc2.changeDetails.updateTimestamp)

        val convertedPermission2 = permissionAssoc2.permission
        assertEquals("perm2", convertedPermission2.id)
        assertEquals(later, convertedPermission2.lastChangeDetails.updateTimestamp)
        assertNull(convertedPermission2.groupVisibility)
        assertEquals("virtNode4", convertedPermission2.virtualNode)
        assertEquals(AvroPermissionType.DENY, convertedPermission2.type)
        assertEquals("*", convertedPermission2.permissionString)

    }

    @Test
    fun `convert model group to avro group`() {
        val now = Instant.now().truncatedTo(ChronoUnit.MILLIS)
        val later = Instant.now().truncatedTo(ChronoUnit.MILLIS)

        val group = Group(
            "group1",
            now,
            "groupName1",
            null
        )
        val role = Role(
            "role1",
            now,
            "roleName1",
            group,
        )
        val roleGroupAssociation = RoleGroupAssociation(
            "rga1",
            role,
            group,
            later
        )
        val groupProperty = GroupProperty(
            "groupProp1",
            now,
            group,
            "gkey1",
            "gval1"
        )
        group.roleGroupAssociations.add(roleGroupAssociation)
        group.groupProperties.add(groupProperty)

        val avroGroup = group.toAvroGroup()

        assertEquals("group1", avroGroup.id)
        assertEquals(now, avroGroup.lastChangeDetails.updateTimestamp)
        assertEquals("groupName1", avroGroup.name)
        assertNull(avroGroup.parentGroupId)

        assertEquals(1, avroGroup.roleAssociations.size)
        assertEquals("role1", avroGroup.roleAssociations[0].roleId)
        assertEquals(later, avroGroup.roleAssociations[0].changeDetails.updateTimestamp)

        assertEquals(1, avroGroup.properties.size)
        assertEquals("groupProp1", avroGroup.properties[0].id)
        assertEquals(now, avroGroup.properties[0].lastChangeDetails.updateTimestamp)
        assertEquals("gkey1", avroGroup.properties[0].key)
        assertEquals("gval1", avroGroup.properties[0].value)
    }
}