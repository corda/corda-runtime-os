package net.corda.libs.permissions.storage.reader.impl

import net.corda.data.permissions.ChangeDetails
import net.corda.libs.permissions.cache.PermissionCache
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
import net.corda.rpc.schema.Schema
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.time.Instant
import javax.persistence.EntityManager
import javax.persistence.EntityManagerFactory
import javax.persistence.EntityTransaction
import javax.persistence.TypedQuery

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

        val avroPermission = net.corda.data.permissions.Permission(
            permission.id,
            -1,
            ChangeDetails(permission.updateTimestamp, "Need to get the changed by user from somewhere"),
            permission.virtualNode,
            permission.permissionString,
            net.corda.data.permissions.PermissionType.ALLOW
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

        val avroRole = net.corda.data.permissions.Role(
            role.id,
            -1,
            ChangeDetails(role.updateTimestamp, "Need to get the changed by user from somewhere"),
            role.name,
            listOf(avroPermission)
        )

        val avroRole2 = net.corda.data.permissions.Role(
            role2.id,
            -1,
            ChangeDetails(role2.updateTimestamp, "Need to get the changed by user from somewhere"),
            role2.name,
            listOf(avroPermission)
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

        val avroGroup = net.corda.data.permissions.Group(
            group.id,
            -1,
            ChangeDetails(group.updateTimestamp, "Need to get the changed by user from somewhere"),
            group.name,
            parentGroup.id,
            listOf(
                net.corda.data.permissions.Property(
                    groupProperty.id,
                    -1,
                    ChangeDetails(groupProperty.updateTimestamp, "Need to get the changed by user from somewhere"),
                    groupProperty.key,
                    groupProperty.value
                )
            ),
            listOf(role.id)
        )

        val avroGroup2 = net.corda.data.permissions.Group(
            group2.id,
            -1,
            ChangeDetails(group2.updateTimestamp, "Need to get the changed by user from somewhere"),
            group2.name,
            parentGroup.id,
            listOf(
                net.corda.data.permissions.Property(
                    groupProperty.id,
                    -1,
                    ChangeDetails(groupProperty.updateTimestamp, "Need to get the changed by user from somewhere"),
                    groupProperty.key,
                    groupProperty.value
                )
            ),
            listOf(role.id)
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

        val avroUser = net.corda.data.permissions.User(
            user.id,
            -1,
            ChangeDetails(user.updateTimestamp, "Need to get the changed by user from somewhere"),
            user.fullName,
            user.enabled,
            user.hashedPassword,
            user.saltValue,
            false,
            group.id,
            emptyList(),
            listOf(role.id)
        )

        val avroUser2 = net.corda.data.permissions.User(
            user2.id,
            -1,
            ChangeDetails(user2.updateTimestamp, "Need to get the changed by user from somewhere"),
            user2.fullName,
            user2.enabled,
            user2.hashedPassword,
            user2.saltValue,
            false,
            group.id,
            emptyList(),
            listOf(role.id)
        )
    }

    private val permissionCache = mock<PermissionCache>()
    private val transaction = mock<EntityTransaction>()
    private val entityManager = mock<EntityManager>().apply {
        whenever(transaction).thenReturn(this@PermissionStorageReaderImplTest.transaction)
    }
    private val entityManagerFactory = mock<EntityManagerFactory>().apply {
        whenever(createEntityManager()).thenReturn(entityManager)
    }
    private val userQuery = mock<TypedQuery<User>>()
    private val groupQuery = mock<TypedQuery<Group>>()
    private val roleQuery = mock<TypedQuery<Role>>()
    private val publisher = mock<Publisher>()

    private val processor = PermissionStorageReaderImpl(permissionCache, entityManagerFactory, publisher)

    @Test
    fun `starting the reader publishes stored users, groups and roles`() {
        whenever(permissionCache.users).thenReturn(emptyMap())
        whenever(permissionCache.groups).thenReturn(emptyMap())
        whenever(permissionCache.roles).thenReturn(emptyMap())
        whenever(userQuery.resultList).thenReturn(listOf(user))
        whenever(groupQuery.resultList).thenReturn(listOf(group))
        whenever(roleQuery.resultList).thenReturn(listOf(role))
        whenever(entityManager.createQuery(any(), eq(User::class.java))).thenReturn(userQuery)
        whenever(entityManager.createQuery(any(), eq(Group::class.java))).thenReturn(groupQuery)
        whenever(entityManager.createQuery(any(), eq(Role::class.java))).thenReturn(roleQuery)

        processor.start()

        val userRecord = Record(Schema.RPC_PERM_USER_TOPIC, user.loginName, avroUser)
        val groupRecord = Record(Schema.RPC_PERM_GROUP_TOPIC, group.name, avroGroup)
        val roleRecord = Record(Schema.RPC_PERM_ROLE_TOPIC, role.name, avroRole)

        verify(publisher).publish(listOf(userRecord, groupRecord, roleRecord))
    }

    @Test
    fun `calling the reader's publish method finds the specified users, groups and roles and publishes them`() {
        val userIds = listOf("user id")
        val groupIds = listOf("group id")
        val roleIds = listOf("role id")

        whenever(permissionCache.users).thenReturn(emptyMap())
        whenever(permissionCache.groups).thenReturn(emptyMap())
        whenever(permissionCache.roles).thenReturn(emptyMap())
        whenever(userQuery.setParameter(any<String>(), eq(userIds))).thenReturn(userQuery)
        whenever(groupQuery.setParameter(any<String>(), eq(groupIds))).thenReturn(groupQuery)
        whenever(roleQuery.setParameter(any<String>(), eq(roleIds))).thenReturn(roleQuery)
        whenever(userQuery.resultList).thenReturn(listOf(user))
        whenever(groupQuery.resultList).thenReturn(listOf(group))
        whenever(roleQuery.resultList).thenReturn(listOf(role))
        whenever(entityManager.createQuery(any(), eq(User::class.java))).thenReturn(userQuery)
        whenever(entityManager.createQuery(any(), eq(Group::class.java))).thenReturn(groupQuery)
        whenever(entityManager.createQuery(any(), eq(Role::class.java))).thenReturn(roleQuery)

        processor.publish(userIds, groupIds, roleIds)

        val userRecord = Record(Schema.RPC_PERM_USER_TOPIC, user.loginName, avroUser)
        val groupRecord = Record(Schema.RPC_PERM_GROUP_TOPIC, group.name, avroGroup)
        val roleRecord = Record(Schema.RPC_PERM_ROLE_TOPIC, role.name, avroRole)

        verify(publisher).publish(listOf(userRecord, groupRecord, roleRecord))
    }

    @Test
    fun `publish diffs the permission cache and removes any specified user records that were deleted`() {
        val userIds = listOf("user id")
        val groupIds = listOf("group id")
        val roleIds = listOf("role id")

        whenever(permissionCache.users).thenReturn(mapOf(user.loginName to avroUser))
        whenever(permissionCache.groups).thenReturn(emptyMap())
        whenever(permissionCache.roles).thenReturn(emptyMap())
        whenever(userQuery.setParameter(any<String>(), eq(userIds))).thenReturn(userQuery)
        whenever(groupQuery.setParameter(any<String>(), eq(groupIds))).thenReturn(groupQuery)
        whenever(roleQuery.setParameter(any<String>(), eq(roleIds))).thenReturn(roleQuery)
        whenever(userQuery.resultList).thenReturn(emptyList())
        whenever(groupQuery.resultList).thenReturn(listOf(group))
        whenever(roleQuery.resultList).thenReturn(listOf(role))
        whenever(entityManager.createQuery(any(), eq(User::class.java))).thenReturn(userQuery)
        whenever(entityManager.createQuery(any(), eq(Group::class.java))).thenReturn(groupQuery)
        whenever(entityManager.createQuery(any(), eq(Role::class.java))).thenReturn(roleQuery)

        processor.publish(userIds, groupIds, roleIds)

        val userRecord = Record(Schema.RPC_PERM_USER_TOPIC, user.loginName, value = null)
        val groupRecord = Record(Schema.RPC_PERM_GROUP_TOPIC, group.name, avroGroup)
        val roleRecord = Record(Schema.RPC_PERM_ROLE_TOPIC, role.name, avroRole)

        verify(publisher).publish(listOf(userRecord, groupRecord, roleRecord))
    }

    @Test
    fun `publish diffs the permission cache and removes any specified groups records that were deleted`() {
        val userIds = listOf("user id")
        val groupIds = listOf("group id")
        val roleIds = listOf("role id")

        whenever(permissionCache.users).thenReturn(emptyMap())
        whenever(permissionCache.groups).thenReturn(mapOf(avroGroup.name to avroGroup))
        whenever(permissionCache.roles).thenReturn(emptyMap())
        whenever(userQuery.setParameter(any<String>(), eq(userIds))).thenReturn(userQuery)
        whenever(groupQuery.setParameter(any<String>(), eq(groupIds))).thenReturn(groupQuery)
        whenever(roleQuery.setParameter(any<String>(), eq(roleIds))).thenReturn(roleQuery)
        whenever(userQuery.resultList).thenReturn(listOf(user))
        whenever(groupQuery.resultList).thenReturn(emptyList())
        whenever(roleQuery.resultList).thenReturn(listOf(role))
        whenever(entityManager.createQuery(any(), eq(User::class.java))).thenReturn(userQuery)
        whenever(entityManager.createQuery(any(), eq(Group::class.java))).thenReturn(groupQuery)
        whenever(entityManager.createQuery(any(), eq(Role::class.java))).thenReturn(roleQuery)

        processor.publish(userIds, groupIds, roleIds)

        val userRecord = Record(Schema.RPC_PERM_USER_TOPIC, user.loginName, avroUser)
        val groupRecord = Record(Schema.RPC_PERM_GROUP_TOPIC, group.name, value = null)
        val roleRecord = Record(Schema.RPC_PERM_ROLE_TOPIC, role.name, avroRole)

        verify(publisher).publish(listOf(userRecord, groupRecord, roleRecord))
    }

    @Test
    fun `publish diffs the permission cache and removes any specified roles records that were deleted`() {
        val userIds = listOf("user id")
        val groupIds = listOf("group id")
        val roleIds = listOf("role id")

        whenever(permissionCache.users).thenReturn(emptyMap())
        whenever(permissionCache.groups).thenReturn(emptyMap())
        whenever(permissionCache.roles).thenReturn(mapOf(avroRole.name to avroRole))
        whenever(userQuery.setParameter(any<String>(), eq(userIds))).thenReturn(userQuery)
        whenever(groupQuery.setParameter(any<String>(), eq(groupIds))).thenReturn(groupQuery)
        whenever(roleQuery.setParameter(any<String>(), eq(roleIds))).thenReturn(roleQuery)
        whenever(userQuery.resultList).thenReturn(listOf(user))
        whenever(groupQuery.resultList).thenReturn(listOf(group))
        whenever(roleQuery.resultList).thenReturn(emptyList())
        whenever(entityManager.createQuery(any(), eq(User::class.java))).thenReturn(userQuery)
        whenever(entityManager.createQuery(any(), eq(Group::class.java))).thenReturn(groupQuery)
        whenever(entityManager.createQuery(any(), eq(Role::class.java))).thenReturn(roleQuery)

        processor.publish(userIds, groupIds, roleIds)

        val userRecord = Record(Schema.RPC_PERM_USER_TOPIC, user.loginName, avroUser)
        val groupRecord = Record(Schema.RPC_PERM_GROUP_TOPIC, group.name, avroGroup)
        val roleRecord = Record(Schema.RPC_PERM_ROLE_TOPIC, role.name, value = null)

        verify(publisher).publish(listOf(userRecord, groupRecord, roleRecord))
    }

    @Test
    fun `publish allows users to be updated and removed at the same time`() {
        val userIds = listOf("user id")
        val groupIds = listOf("group id")
        val roleIds = listOf("role id")

        whenever(permissionCache.users).thenReturn(mapOf(user.loginName to avroUser, user2.loginName to avroUser2))
        whenever(permissionCache.groups).thenReturn(emptyMap())
        whenever(permissionCache.roles).thenReturn(emptyMap())
        whenever(userQuery.setParameter(any<String>(), eq(userIds))).thenReturn(userQuery)
        whenever(groupQuery.setParameter(any<String>(), eq(groupIds))).thenReturn(groupQuery)
        whenever(roleQuery.setParameter(any<String>(), eq(roleIds))).thenReturn(roleQuery)
        whenever(userQuery.resultList).thenReturn(listOf(user))
        whenever(groupQuery.resultList).thenReturn(listOf(group))
        whenever(roleQuery.resultList).thenReturn(listOf(role))
        whenever(entityManager.createQuery(any(), eq(User::class.java))).thenReturn(userQuery)
        whenever(entityManager.createQuery(any(), eq(Group::class.java))).thenReturn(groupQuery)
        whenever(entityManager.createQuery(any(), eq(Role::class.java))).thenReturn(roleQuery)

        processor.publish(userIds, groupIds, roleIds)

        val userRecords = listOf(
            Record(Schema.RPC_PERM_USER_TOPIC, user.loginName, avroUser),
            Record(Schema.RPC_PERM_USER_TOPIC, user2.loginName, value = null)
        )
        val groupRecord = Record(Schema.RPC_PERM_GROUP_TOPIC, group.name, avroGroup)
        val roleRecord = Record(Schema.RPC_PERM_ROLE_TOPIC, role.name, avroRole)

        verify(publisher).publish(userRecords + listOf(groupRecord, roleRecord))
    }

    @Test
    fun `publish allows groups to be updated and removed at the same time`() {
        val userIds = listOf("user id")
        val groupIds = listOf("group id")
        val roleIds = listOf("role id")

        whenever(permissionCache.users).thenReturn(emptyMap())
        whenever(permissionCache.groups).thenReturn(mapOf(group.name to avroGroup, group2.name to avroGroup2))
        whenever(permissionCache.roles).thenReturn(emptyMap())
        whenever(userQuery.setParameter(any<String>(), eq(userIds))).thenReturn(userQuery)
        whenever(groupQuery.setParameter(any<String>(), eq(groupIds))).thenReturn(groupQuery)
        whenever(roleQuery.setParameter(any<String>(), eq(roleIds))).thenReturn(roleQuery)
        whenever(userQuery.resultList).thenReturn(listOf(user))
        whenever(groupQuery.resultList).thenReturn(listOf(group))
        whenever(roleQuery.resultList).thenReturn(listOf(role))
        whenever(entityManager.createQuery(any(), eq(User::class.java))).thenReturn(userQuery)
        whenever(entityManager.createQuery(any(), eq(Group::class.java))).thenReturn(groupQuery)
        whenever(entityManager.createQuery(any(), eq(Role::class.java))).thenReturn(roleQuery)

        processor.publish(userIds, groupIds, roleIds)

        val userRecord = Record(Schema.RPC_PERM_USER_TOPIC, user.loginName, avroUser)
        val groupRecords = listOf(
            Record(Schema.RPC_PERM_GROUP_TOPIC, group.name, avroGroup),
            Record(Schema.RPC_PERM_GROUP_TOPIC, group2.name, value = null)
        )
        val roleRecord = Record(Schema.RPC_PERM_ROLE_TOPIC, role.name, avroRole)

        verify(publisher).publish((groupRecords + roleRecord).toMutableList().apply { add(0, userRecord) })
    }

    @Test
    fun `publish allows roles to be updated and removed at the same time`() {
        val userIds = listOf("user id")
        val groupIds = listOf("group id")
        val roleIds = listOf("role id")

        whenever(permissionCache.users).thenReturn(emptyMap())
        whenever(permissionCache.groups).thenReturn(emptyMap())
        whenever(permissionCache.roles).thenReturn(mapOf(role.name to avroRole, role2.name to avroRole2))
        whenever(userQuery.setParameter(any<String>(), eq(userIds))).thenReturn(userQuery)
        whenever(groupQuery.setParameter(any<String>(), eq(groupIds))).thenReturn(groupQuery)
        whenever(roleQuery.setParameter(any<String>(), eq(roleIds))).thenReturn(roleQuery)
        whenever(userQuery.resultList).thenReturn(listOf(user))
        whenever(groupQuery.resultList).thenReturn(listOf(group))
        whenever(roleQuery.resultList).thenReturn(listOf(role))
        whenever(entityManager.createQuery(any(), eq(User::class.java))).thenReturn(userQuery)
        whenever(entityManager.createQuery(any(), eq(Group::class.java))).thenReturn(groupQuery)
        whenever(entityManager.createQuery(any(), eq(Role::class.java))).thenReturn(roleQuery)

        processor.publish(userIds, groupIds, roleIds)

        val userRecord = Record(Schema.RPC_PERM_USER_TOPIC, user.loginName, avroUser)
        val groupRecord = Record(Schema.RPC_PERM_GROUP_TOPIC, group.name, avroGroup)
        val roleRecords = listOf(
            Record(Schema.RPC_PERM_ROLE_TOPIC, role.name, avroRole),
            Record(Schema.RPC_PERM_ROLE_TOPIC, role2.name, value = null)
        )

        verify(publisher).publish(listOf(userRecord, groupRecord) + roleRecords)
    }

    @Test
    fun `no records are published if no records must be updated or removed`() {
        whenever(permissionCache.users).thenReturn(emptyMap())
        whenever(permissionCache.groups).thenReturn(emptyMap())
        whenever(permissionCache.roles).thenReturn(emptyMap())
        whenever(userQuery.resultList).thenReturn(emptyList())
        whenever(groupQuery.resultList).thenReturn(emptyList())
        whenever(roleQuery.resultList).thenReturn(emptyList())
        whenever(entityManager.createQuery(any(), eq(User::class.java))).thenReturn(userQuery)
        whenever(entityManager.createQuery(any(), eq(Group::class.java))).thenReturn(groupQuery)
        whenever(entityManager.createQuery(any(), eq(Role::class.java))).thenReturn(roleQuery)

        processor.start()

        verify(publisher, never()).publish(any())
    }

    @Test
    fun `records are published if users exist`() {
        whenever(permissionCache.users).thenReturn(emptyMap())
        whenever(permissionCache.groups).thenReturn(emptyMap())
        whenever(permissionCache.roles).thenReturn(emptyMap())
        whenever(userQuery.resultList).thenReturn(listOf(user))
        whenever(groupQuery.resultList).thenReturn(emptyList())
        whenever(roleQuery.resultList).thenReturn(emptyList())
        whenever(entityManager.createQuery(any(), eq(User::class.java))).thenReturn(userQuery)
        whenever(entityManager.createQuery(any(), eq(Group::class.java))).thenReturn(groupQuery)
        whenever(entityManager.createQuery(any(), eq(Role::class.java))).thenReturn(roleQuery)

        processor.start()

        verify(publisher).publish(any())
    }

    @Test
    fun `records are published if users must be removed`() {
        whenever(permissionCache.users).thenReturn(mapOf(user.loginName to avroUser))
        whenever(permissionCache.groups).thenReturn(emptyMap())
        whenever(permissionCache.roles).thenReturn(emptyMap())
        whenever(userQuery.resultList).thenReturn(emptyList())
        whenever(groupQuery.resultList).thenReturn(emptyList())
        whenever(roleQuery.resultList).thenReturn(emptyList())
        whenever(entityManager.createQuery(any(), eq(User::class.java))).thenReturn(userQuery)
        whenever(entityManager.createQuery(any(), eq(Group::class.java))).thenReturn(groupQuery)
        whenever(entityManager.createQuery(any(), eq(Role::class.java))).thenReturn(roleQuery)

        processor.start()

        verify(publisher).publish(any())
    }

    @Test
    fun `records are published if groups exist`() {
        whenever(permissionCache.users).thenReturn(emptyMap())
        whenever(permissionCache.groups).thenReturn(emptyMap())
        whenever(permissionCache.roles).thenReturn(emptyMap())
        whenever(userQuery.resultList).thenReturn(emptyList())
        whenever(groupQuery.resultList).thenReturn(listOf(group))
        whenever(roleQuery.resultList).thenReturn(emptyList())
        whenever(entityManager.createQuery(any(), eq(User::class.java))).thenReturn(userQuery)
        whenever(entityManager.createQuery(any(), eq(Group::class.java))).thenReturn(groupQuery)
        whenever(entityManager.createQuery(any(), eq(Role::class.java))).thenReturn(roleQuery)

        processor.start()

        verify(publisher).publish(any())
    }

    @Test
    fun `records are published if groups must be removed`() {
        whenever(permissionCache.users).thenReturn(emptyMap())
        whenever(permissionCache.groups).thenReturn(mapOf(group.name to avroGroup))
        whenever(permissionCache.roles).thenReturn(emptyMap())
        whenever(userQuery.resultList).thenReturn(emptyList())
        whenever(groupQuery.resultList).thenReturn(emptyList())
        whenever(roleQuery.resultList).thenReturn(emptyList())
        whenever(entityManager.createQuery(any(), eq(User::class.java))).thenReturn(userQuery)
        whenever(entityManager.createQuery(any(), eq(Group::class.java))).thenReturn(groupQuery)
        whenever(entityManager.createQuery(any(), eq(Role::class.java))).thenReturn(roleQuery)

        processor.start()

        verify(publisher).publish(any())
    }

    @Test
    fun `records are published if roles exist`() {
        whenever(permissionCache.users).thenReturn(emptyMap())
        whenever(permissionCache.groups).thenReturn(emptyMap())
        whenever(permissionCache.roles).thenReturn(emptyMap())
        whenever(userQuery.resultList).thenReturn(emptyList())
        whenever(groupQuery.resultList).thenReturn(emptyList())
        whenever(roleQuery.resultList).thenReturn(listOf(role))
        whenever(entityManager.createQuery(any(), eq(User::class.java))).thenReturn(userQuery)
        whenever(entityManager.createQuery(any(), eq(Group::class.java))).thenReturn(groupQuery)
        whenever(entityManager.createQuery(any(), eq(Role::class.java))).thenReturn(roleQuery)

        processor.start()

        verify(publisher).publish(any())
    }

    @Test
    fun `records are published if roles must be removed`() {
        whenever(permissionCache.users).thenReturn(emptyMap())
        whenever(permissionCache.groups).thenReturn(emptyMap())
        whenever(permissionCache.roles).thenReturn(mapOf(role.name to avroRole))
        whenever(userQuery.resultList).thenReturn(emptyList())
        whenever(groupQuery.resultList).thenReturn(emptyList())
        whenever(roleQuery.resultList).thenReturn(emptyList())
        whenever(entityManager.createQuery(any(), eq(User::class.java))).thenReturn(userQuery)
        whenever(entityManager.createQuery(any(), eq(Group::class.java))).thenReturn(groupQuery)
        whenever(entityManager.createQuery(any(), eq(Role::class.java))).thenReturn(roleQuery)

        processor.start()

        verify(publisher).publish(any())
    }
}