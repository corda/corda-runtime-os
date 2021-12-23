package net.corda.libs.permissions.storage.reader.impl

import net.corda.data.permissions.ChangeDetails
import net.corda.data.permissions.PermissionAssociation
import net.corda.data.permissions.RoleAssociation
import net.corda.libs.permissions.cache.PermissionCache
import net.corda.libs.permissions.storage.reader.repository.PermissionRepository
import net.corda.messaging.api.publisher.Publisher
import net.corda.messaging.api.records.Record
import net.corda.permissions.model.Group
import net.corda.permissions.model.GroupProperty
import net.corda.permissions.model.Permission
import net.corda.permissions.model.PermissionType
import net.corda.permissions.model.Role
import net.corda.permissions.model.RoleGroupAssociation
import net.corda.permissions.model.RolePermissionAssociation
import net.corda.permissions.model.RoleUserAssociation
import net.corda.permissions.model.User
import net.corda.schema.Schemas.RPC.Companion.RPC_PERM_ENTITY_TOPIC
import net.corda.schema.Schemas.RPC.Companion.RPC_PERM_GROUP_TOPIC
import net.corda.schema.Schemas.RPC.Companion.RPC_PERM_ROLE_TOPIC
import net.corda.schema.Schemas.RPC.Companion.RPC_PERM_USER_TOPIC
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.mock
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.time.Instant
import net.corda.data.permissions.Group as AvroGroup
import net.corda.data.permissions.Permission as AvroPermission
import net.corda.data.permissions.PermissionType as AvroPermissionType
import net.corda.data.permissions.Role as AvroRole
import net.corda.data.permissions.User as AvroUser

class PermissionStorageReaderImplTest {

    private companion object {

        val parentGroup = Group(
            id = "parent group id",
            updateTimestamp = Instant.now(),
            name = "Anime character",
            parentGroup = null
        )

        val permission = Permission(
            id = "permission id",
            updateTimestamp = Instant.now(),
            groupVisibility = parentGroup,
            virtualNode = "virtual node",
            permissionType = PermissionType.ALLOW,
            permissionString = "URL:/*"
        )

        val avroPermission = AvroPermission(
            permission.id,
            -1,
            ChangeDetails(permission.updateTimestamp),
            permission.virtualNode,
            AvroPermissionType.ALLOW,
            permission.permissionString,
            parentGroup.id
        )

        val role = Role(
            id = "role id",
            updateTimestamp = Instant.now(),
            name = "Gluttony",
            groupVisibility = parentGroup
        ).apply {
            rolePermAssociations = mutableSetOf(
                RolePermissionAssociation(
                    id = "role permission association id",
                    role = this,
                    permission = permission,
                    updateTimestamp = Instant.now()
                )
            )
        }

        val role2 = Role(
            id = "role id 2",
            updateTimestamp = Instant.now(),
            name = "Magic user",
            groupVisibility = parentGroup
        ).apply {
            rolePermAssociations = mutableSetOf(
                RolePermissionAssociation(
                    id = "role permission association id",
                    role = this,
                    permission = permission,
                    updateTimestamp = Instant.now()
                )
            )
        }

        val avroRole = AvroRole(
            role.id,
            -1,
            ChangeDetails(role.updateTimestamp),
            role.name,
            parentGroup.id,
            listOf(
                PermissionAssociation(
                    ChangeDetails(role.rolePermAssociations.first().updateTimestamp),
                    avroPermission.id
                )
            )
        )

        val avroRole2 = AvroRole(
            role2.id,
            -1,
            ChangeDetails(role2.updateTimestamp),
            role2.name,
            parentGroup.id,
            listOf(
                PermissionAssociation(
                    ChangeDetails(role2.rolePermAssociations.first().updateTimestamp),
                    avroPermission.id
                )
            )
        )

        val group = Group(
            id = "group id",
            updateTimestamp = Instant.now(),
            name = "Ruler",
            parentGroup = parentGroup
        ).apply {
            roleGroupAssociations = mutableSetOf(
                RoleGroupAssociation(
                    id = "role group association id",
                    role = role,
                    group = this,
                    updateTimestamp = Instant.now()
                )
            )
        }

        val group2 = Group(
            id = "group id 2",
            updateTimestamp = Instant.now(),
            name = "Leader",
            parentGroup = parentGroup
        ).apply {
            roleGroupAssociations = mutableSetOf(
                RoleGroupAssociation(
                    id = "role group association id",
                    role = role,
                    group = this,
                    updateTimestamp = Instant.now()
                )
            )
        }

        val groupProperty = GroupProperty(
            id = "group property id",
            updateTimestamp = Instant.now(),
            groupRef = group,
            key = "key",
            value = "value"
        ).apply {
            group.groupProperties = mutableSetOf(this)
        }

        val avroGroup = AvroGroup(
            group.id,
            -1,
            ChangeDetails(group.updateTimestamp),
            group.name,
            parentGroup.id,
            listOf(
                net.corda.data.permissions.Property(
                    groupProperty.id,
                    -1,
                    ChangeDetails(groupProperty.updateTimestamp),
                    groupProperty.key,
                    groupProperty.value
                )
            ),
            listOf(RoleAssociation(ChangeDetails(group.roleGroupAssociations.first().updateTimestamp), role.id))
        )

        val avroGroup2 = AvroGroup(
            group2.id,
            -1,
            ChangeDetails(group2.updateTimestamp),
            group2.name,
            parentGroup.id,
            listOf(
                net.corda.data.permissions.Property(
                    groupProperty.id,
                    -1,
                    ChangeDetails(groupProperty.updateTimestamp),
                    groupProperty.key,
                    groupProperty.value
                )
            ),
            listOf(RoleAssociation(ChangeDetails(Instant.now()), role.id))
        )

        val user = User(
            id = "user id",
            updateTimestamp = Instant.now(),
            fullName = "Rimiru Tempest",
            loginName = "Slime",
            enabled = true,
            saltValue = null,
            hashedPassword = null,
            passwordExpiry = null,
            parentGroup = group
        ).apply {
            roleUserAssociations = mutableSetOf(
                RoleUserAssociation(
                    id = "role user association id",
                    role = role,
                    user = this,
                    updateTimestamp = Instant.now()
                )
            )
        }

        val user2 = User(
            id = "user id 2",
            updateTimestamp = Instant.now(),
            fullName = "Benimaru",
            loginName = "Benimaru",
            enabled = true,
            saltValue = null,
            hashedPassword = null,
            passwordExpiry = null,
            parentGroup = group
        ).apply {
            roleUserAssociations = mutableSetOf(
                RoleUserAssociation(
                    id = "role user association id",
                    role = role,
                    user = this,
                    updateTimestamp = Instant.now()
                )
            )
        }

        val avroUser = AvroUser(
            user.id,
            -1,
            ChangeDetails(user.updateTimestamp),
            user.loginName,
            user.fullName,
            user.enabled,
            user.hashedPassword,
            user.saltValue,
            user.passwordExpiry,
            true,
            group.id,
            emptyList(),
            listOf(
                RoleAssociation(
                    ChangeDetails(user.roleUserAssociations.first().updateTimestamp),
                    user.roleUserAssociations.first().role.id
                )
            )
        )

        val avroUser2 = AvroUser(
            user2.id,
            -1,
            ChangeDetails(user2.updateTimestamp),
            user2.loginName,
            user2.fullName,
            user2.enabled,
            user2.hashedPassword,
            user2.saltValue,
            user2.passwordExpiry,
            true,
            group.id,
            emptyList(),
            listOf(
                RoleAssociation(
                    ChangeDetails(user2.roleUserAssociations.first().updateTimestamp),
                    user2.roleUserAssociations.first().role.id
                )
            )
        )
    }

    private val permissionCache = mock<PermissionCache>()
    private val permissionRepository = mock<PermissionRepository>()
    private val publisher = mock<Publisher>()

    private val processor = PermissionStorageReaderImpl(permissionCache, permissionRepository, publisher)

    @Test
    fun `starting the reader publishes stored users, groups, roles and permissions`() {
        whenever(permissionCache.users).thenReturn(emptyMap())
        whenever(permissionCache.groups).thenReturn(emptyMap())
        whenever(permissionCache.roles).thenReturn(emptyMap())
        whenever(permissionCache.permissions).thenReturn(emptyMap())
        whenever(permissionRepository.findAllUsers()).thenReturn(listOf(user))
        whenever(permissionRepository.findAllGroups()).thenReturn(listOf(group))
        whenever(permissionRepository.findAllRoles()).thenReturn(listOf(role))
        whenever(permissionRepository.findAllPermissions()).thenReturn(listOf(permission))

        processor.start()

        val userRecord = Record(RPC_PERM_USER_TOPIC, user.loginName, avroUser)
        val groupRecord = Record(RPC_PERM_GROUP_TOPIC, group.id, avroGroup)
        val roleRecord = Record(RPC_PERM_ROLE_TOPIC, role.id, avroRole)
        val permissionRecord = Record(RPC_PERM_ENTITY_TOPIC, permission.id, avroPermission)

        val captor = argumentCaptor<List<Record<String, Any>>>()
        verify(publisher, times(4)).publish(captor.capture())
        assertEquals(listOf(userRecord), captor.firstValue)
        assertEquals(listOf(groupRecord), captor.secondValue)
        assertEquals(listOf(roleRecord), captor.thirdValue)
        assertEquals(listOf(permissionRecord), captor.allValues[3])
    }

    @Test
    fun `starting the reader diffs the permission cache and removes any users, groups, roles and permissions deleted`() {
        whenever(permissionCache.users).thenReturn(mapOf(user.loginName to avroUser))
        whenever(permissionCache.groups).thenReturn(mapOf(avroGroup.id to avroGroup))
        whenever(permissionCache.roles).thenReturn(mapOf(avroRole.id to avroRole))
        whenever(permissionCache.permissions).thenReturn(mapOf(avroPermission.id to avroPermission))
        whenever(permissionRepository.findAllUsers()).thenReturn(emptyList())
        whenever(permissionRepository.findAllGroups()).thenReturn(emptyList())
        whenever(permissionRepository.findAllRoles()).thenReturn(emptyList())
        whenever(permissionRepository.findAllPermissions()).thenReturn(emptyList())

        processor.start()

        val userRecord = Record(RPC_PERM_USER_TOPIC, user.loginName, value = null)
        val groupRecord = Record(RPC_PERM_GROUP_TOPIC, group.id, value = null)
        val roleRecord = Record(RPC_PERM_ROLE_TOPIC, role.id, value = null)
        val permissionRecord = Record(RPC_PERM_ENTITY_TOPIC, permission.id, value = null)

        val captor = argumentCaptor<List<Record<String, Any>>>()
        verify(publisher, times(4)).publish(captor.capture())
        assertEquals(listOf(userRecord), captor.firstValue)
        assertEquals(listOf(groupRecord), captor.secondValue)
        assertEquals(listOf(roleRecord), captor.thirdValue)
        assertEquals(listOf(permissionRecord), captor.allValues[3])
    }

    @Test
    fun `publishUsers finds the specified users and publishes them`() {

        whenever(permissionCache.users).thenReturn(emptyMap())
        whenever(permissionRepository.findAllUsers()).thenReturn(listOf(user))

        processor.start()

        val userRecord = Record(RPC_PERM_USER_TOPIC, user.loginName, avroUser)

        verify(publisher).publish(listOf(userRecord))
    }

    @Test
    fun `publishGroups finds the specified groups and publishes them`() {
        val groupIds = listOf("group id")

        whenever(permissionCache.groups).thenReturn(emptyMap())
        whenever(permissionRepository.findAllGroups(groupIds)).thenReturn(listOf(group))

        processor.publishGroups(groupIds)

        val groupRecord = Record(RPC_PERM_GROUP_TOPIC, group.id, avroGroup)

        verify(publisher).publish(listOf(groupRecord))
    }

    @Test
    fun `publishRoles finds the specified roles and publishes them`() {
        val roleIds = listOf("role id")

        whenever(permissionCache.roles).thenReturn(emptyMap())
        whenever(permissionRepository.findAllRoles(roleIds)).thenReturn(listOf(role))

        processor.publishRoles(roleIds)

        val roleRecord = Record(RPC_PERM_ROLE_TOPIC, role.id, avroRole)

        verify(publisher).publish(listOf(roleRecord))
    }

    @Test
    fun `publishUsers diffs the permission cache and removes any specified user records that were deleted`() {
        whenever(permissionCache.users).thenReturn(mapOf(user.loginName to avroUser))
        whenever(permissionRepository.findAllUsers()).thenReturn(emptyList())

        processor.start()

        val userRecord = Record(RPC_PERM_USER_TOPIC, user.loginName, value = null)

        verify(publisher).publish(listOf(userRecord))
    }

    @Test
    fun `publishGroups diffs the permission cache and removes any specified groups records that were deleted`() {
        val groupIds = listOf("group id")

        whenever(permissionCache.groups).thenReturn(mapOf(avroGroup.id to avroGroup))
        whenever(permissionRepository.findAllGroups(groupIds)).thenReturn(emptyList())

        processor.publishGroups(groupIds)

        val groupRecord = Record(RPC_PERM_GROUP_TOPIC, group.id, value = null)

        verify(publisher).publish(listOf(groupRecord))
    }

    @Test
    fun `publishRoles diffs the permission cache and removes any specified roles records that were deleted`() {
        val roleIds = listOf("role id")

        whenever(permissionCache.roles).thenReturn(mapOf(avroRole.id to avroRole))
        whenever(permissionRepository.findAllRoles(roleIds)).thenReturn(emptyList())

        processor.publishRoles(roleIds)

        val roleRecord = Record(RPC_PERM_ROLE_TOPIC, role.id, value = null)

        verify(publisher).publish(listOf(roleRecord))
    }

    @Test
    fun `publishUsers allows users to be updated and removed at the same time`() {

        whenever(permissionCache.users).thenReturn(mapOf(user.loginName to avroUser, user2.loginName to avroUser2))
        whenever(permissionRepository.findAllUsers()).thenReturn(listOf(user))

        processor.start()

        val userRecords = listOf(
            Record(RPC_PERM_USER_TOPIC, user.loginName, avroUser),
            Record(RPC_PERM_USER_TOPIC, user2.loginName, value = null)
        )

        verify(publisher).publish(userRecords)
    }

    @Test
    fun `publishGroups allows groups to be updated and removed at the same time`() {
        val groupIds = listOf("group id")

        whenever(permissionCache.groups).thenReturn(mapOf(group.id to avroGroup, group2.id to avroGroup2))
        whenever(permissionRepository.findAllGroups(groupIds)).thenReturn(listOf(group))

        processor.publishGroups(groupIds)

        val groupRecords = listOf(
            Record(RPC_PERM_GROUP_TOPIC, group.id, avroGroup),
            Record(RPC_PERM_GROUP_TOPIC, group2.id, value = null)
        )

        verify(publisher).publish(groupRecords)
    }

    @Test
    fun `publish allows roles to be updated and removed at the same time`() {
        val roleIds = listOf("role id")

        whenever(permissionCache.roles).thenReturn(mapOf(role.id to avroRole, role2.id to avroRole2))
        whenever(permissionRepository.findAllRoles(roleIds)).thenReturn(listOf(role))

        processor.publishRoles(roleIds)

        val roleRecords = listOf(
            Record(RPC_PERM_ROLE_TOPIC, role.id, avroRole),
            Record(RPC_PERM_ROLE_TOPIC, role2.id, value = null)
        )

        verify(publisher).publish(roleRecords)
    }
}