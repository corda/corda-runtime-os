package net.corda.libs.permissions.storage.writer.impl.group

import net.corda.data.permissions.management.group.AddRoleToGroupRequest
import net.corda.data.permissions.management.group.ChangeGroupParentIdRequest
import net.corda.data.permissions.management.group.CreateGroupRequest
import net.corda.data.permissions.management.group.DeleteGroupRequest
import net.corda.data.permissions.management.group.RemoveRoleFromGroupRequest
import net.corda.libs.permissions.common.exception.EntityAssociationAlreadyExistsException
import net.corda.libs.permissions.common.exception.EntityAssociationDoesNotExistException
import net.corda.libs.permissions.common.exception.EntityNotFoundException
import net.corda.libs.permissions.common.exception.IllegalEntityStateException
import net.corda.libs.permissions.storage.writer.impl.group.impl.GroupWriterImpl
import net.corda.permissions.model.ChangeAudit
import net.corda.permissions.model.Group
import net.corda.permissions.model.RestPermissionOperation
import net.corda.permissions.model.Role
import net.corda.permissions.model.RoleGroupAssociation
import net.corda.permissions.model.User
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.mockito.kotlin.*
import java.time.Instant
import javax.persistence.EntityManager
import javax.persistence.EntityManagerFactory
import javax.persistence.EntityTransaction
import javax.persistence.TypedQuery
import kotlin.test.assertContains

class GroupWriterImplTest {

    private val entityManager = mock<EntityManager>()
    private val entityManagerFactory = mock<EntityManagerFactory> {
        on { createEntityManager() }.thenReturn(entityManager)
    }
    private val groupWriter = GroupWriterImpl(entityManagerFactory)
    private val entityTransaction = mock<EntityTransaction>()

    private val requestUserId = "requestUserId"

    @BeforeEach
    fun setup() {
        whenever(entityManager.transaction).thenReturn(entityTransaction)
    }


    @Test
    fun `create a group with no parent specified successfully persists the group`() {
        val createGroupRequest = CreateGroupRequest().apply {
            groupName = "groupName"
        }
        groupWriter.createGroup(createGroupRequest, requestUserId)

        val groupCaptor = argumentCaptor<Group>()
        val auditCaptor = argumentCaptor<ChangeAudit>()

        inOrder(entityTransaction, entityManager) {
            verify(entityTransaction).begin()
            verify(entityManager, times(1)).persist(groupCaptor.capture())
            verify(entityManager, times(1)).persist(auditCaptor.capture())
            verify(entityTransaction).commit()
        }

        val persistedGroup = groupCaptor.firstValue
        assertNotNull(persistedGroup)
        assertEquals("groupName", persistedGroup.name)
        assertNull(persistedGroup.parentGroup)

        val audit = auditCaptor.firstValue
        assertNotNull(audit)
        assertEquals(RestPermissionOperation.GROUP_INSERT, audit.changeType)
        assertEquals("Group '${persistedGroup.name}' created by '$requestUserId'.", audit.details)
    }

    @Test
    fun `create a group with a parent group specified persists a new group to the database`() {
        val createGroupRequest = CreateGroupRequest().apply {
            groupName = "groupId"
            parentGroupId = "parentId"
        }
        val parentGroup = Group("parentId", Instant.now(), "parentGroupName", null)

        whenever(entityManager.find(Group::class.java, "parentId")).thenReturn(parentGroup)

        groupWriter.createGroup(createGroupRequest, requestUserId)

        val groupCaptor = argumentCaptor<Group>()
        val auditCaptor = argumentCaptor<ChangeAudit>()

        inOrder(entityTransaction, entityManager) {
            verify(entityTransaction).begin()
            verify(entityManager, times(1)).persist(groupCaptor.capture())
            verify(entityManager, times(1)).persist(auditCaptor.capture())
            verify(entityTransaction).commit()
        }

        val persistedGroup = groupCaptor.firstValue
        assertNotNull(persistedGroup)
        assertEquals("groupId", persistedGroup.name)
        assertEquals(parentGroup, persistedGroup.parentGroup)

        val audit = auditCaptor.firstValue
        assertNotNull(audit)
        assertEquals(RestPermissionOperation.GROUP_INSERT, audit.changeType)
        assertEquals("Group '${persistedGroup.name}' created by '$requestUserId'.", audit.details)
    }

    @Test
    fun `changing parent group fails if group does not exist`() {
        val changeGroupParentIdRequest = ChangeGroupParentIdRequest().apply {
            groupId = "groupId"
            newParentGroupId = "parentId"
        }

        whenever(entityManager.find(Group::class.java, "groupId")).thenReturn(null)

        val e = assertThrows<EntityNotFoundException> {
            groupWriter.changeParentGroup(changeGroupParentIdRequest, requestUserId)
        }

        assertEquals("Group 'groupId' not found.", e.message)
    }

    @Test
    fun `changing parent group fails if parent group does not exist`() {
        val changeGroupParentIdRequest = ChangeGroupParentIdRequest().apply {
            groupId = "groupId"
            newParentGroupId = "parentId"
        }
        val requestUserId = "requestUserId"
        val group = Group("groupId", Instant.now(), "groupName", null)

        whenever(entityManager.find(Group::class.java, "groupId")).thenReturn(group)
        whenever(entityManager.find(Group::class.java, "parentId")).thenReturn(null)

        assertThrows<EntityNotFoundException>("Group 'parent1' not found.") {
            groupWriter.changeParentGroup(changeGroupParentIdRequest, requestUserId)
        }
    }

    @Test
    fun `changing parent group persists change to group and writes audit log`() {
        val changeGroupParentIdRequest = ChangeGroupParentIdRequest().apply {
            groupId = "groupId"
            newParentGroupId = "parentId"
        }
        val group = Group("groupId", Instant.now(), "groupName", null)
        val parentGroup = Group("parentId", Instant.now(), "parentGroupName", null)

        whenever(entityManager.find(Group::class.java, "groupId")).thenReturn(group)
        whenever(entityManager.find(Group::class.java, "parentId")).thenReturn(parentGroup)

        groupWriter.changeParentGroup(changeGroupParentIdRequest, requestUserId)

        val groupCaptor = argumentCaptor<Group>()
        val auditCaptor = argumentCaptor<ChangeAudit>()

        inOrder(entityTransaction, entityManager) {
            verify(entityTransaction).begin()
            verify(entityManager, times(1)).merge(groupCaptor.capture())
            verify(entityManager, times(1)).persist(auditCaptor.capture())
            verify(entityTransaction).commit()
        }

        val persistedGroup = groupCaptor.firstValue
        assertNotNull(persistedGroup)
        assertEquals("groupName", persistedGroup.name)
        assertEquals(parentGroup, persistedGroup.parentGroup)

        val audit = auditCaptor.firstValue
        assertNotNull(audit)
        assertEquals(RestPermissionOperation.GROUP_UPDATE, audit.changeType)
        assertEquals("Parent group of Group '${persistedGroup.id}' changed to '${parentGroup.id}' by '$requestUserId'.", audit.details)
    }

    @Test
    fun `add role to group fails when group does not exist`() {
        val addRoleToGroupRequest = AddRoleToGroupRequest().apply {
            groupId = "groupId"
            roleId = "roleId"
        }

        whenever(entityManager.find(Group::class.java, "groupId")).thenReturn(null)

        val e = assertThrows<EntityNotFoundException> {
            groupWriter.addRoleToGroup(addRoleToGroupRequest, requestUserId)
        }

        assertEquals("Group 'groupId' not found.", e.message)
    }

    @Test
    fun `add role to group fails when role does not exist`() {
        val addRoleToGroupRequest = AddRoleToGroupRequest().apply {
            groupId = "groupId"
            roleId = "roleId"
        }
        val group = Group("groupId", Instant.now(), "groupName", null)

        whenever(entityManager.find(Group::class.java, "groupId")).thenReturn(group)
        whenever(entityManager.find(Role::class.java, "roleId")).thenReturn(null)

        val e = assertThrows<EntityNotFoundException> {
            groupWriter.addRoleToGroup(addRoleToGroupRequest, requestUserId)
        }

        assertEquals("Role 'roleId' not found.", e.message)
    }

    @Test
    fun `add role to group fails when role is already assigned to group`() {
        val addRoleToGroupRequest = AddRoleToGroupRequest().apply {
            groupId = "groupId"
            roleId = "roleId"
        }
        val role = Role("roleId", Instant.now(), "roleName", null)
        val group = Group("groupId", Instant.now(), "groupName", null)
        group.roleGroupAssociations.add(RoleGroupAssociation("id", role, group, Instant.now()))

        whenever(entityManager.find(Group::class.java, "groupId")).thenReturn(group)
        whenever(entityManager.find(Role::class.java, "roleId")).thenReturn(role)

        val e = assertThrows<EntityAssociationAlreadyExistsException> {
            groupWriter.addRoleToGroup(addRoleToGroupRequest, requestUserId)
        }

        assertEquals("Role 'roleId' is already associated with Group 'groupId'.", e.message)
    }

    @Test
    fun `add role to group successfully persists change to group and audit log`() {
        val addRoleToGroupRequest = AddRoleToGroupRequest().apply {
            groupId = "groupId"
            roleId = "roleId"
        }
        val group = Group("groupId", Instant.now(), "groupName", null)
        val role = Role("roleId", Instant.now(), "roleName", null)

        whenever(entityManager.find(Group::class.java, "groupId")).thenReturn(group)
        whenever(entityManager.find(Role::class.java, "roleId")).thenReturn(role)

        groupWriter.addRoleToGroup(addRoleToGroupRequest, requestUserId)

        val groupCaptor = argumentCaptor<Group>()
        val auditCaptor = argumentCaptor<ChangeAudit>()

        inOrder(entityTransaction, entityManager) {
            verify(entityTransaction).begin()
            verify(entityManager, times(1)).merge(groupCaptor.capture())
            verify(entityManager, times(1)).persist(auditCaptor.capture())
            verify(entityTransaction).commit()
        }

        val persistedGroup = groupCaptor.firstValue
        assertNotNull(persistedGroup)
        assertEquals("groupName", persistedGroup.name)
        assertTrue(persistedGroup.roleGroupAssociations.any { it.role.id == "roleId" })

        val audit = auditCaptor.firstValue
        assertNotNull(audit)
        assertEquals(RestPermissionOperation.ADD_ROLE_TO_GROUP, audit.changeType)
        assertContains(audit.details, "Role 'roleId' assigned to Group 'groupId' by '$requestUserId'. Created RoleGroupAssociation")
    }

    @Test
    fun `remove role from group fails when group does not exist`() {
        val removeRoleFromGroupRequest = RemoveRoleFromGroupRequest().apply {
            groupId = "groupId"
            roleId = "roleId"
        }

        whenever(entityManager.find(Group::class.java, "groupId")).thenReturn(null)

        val e = assertThrows<EntityNotFoundException> {
            groupWriter.removeRoleFromGroup(removeRoleFromGroupRequest, requestUserId)
        }

        assertEquals("Group 'groupId' not found.", e.message)
    }

    @Test
    fun `remove role from group fails when role is not assigned to group`() {
        val removeRoleFromGroupRequest = RemoveRoleFromGroupRequest().apply {
            groupId = "groupId"
            roleId = "roleId"
        }
        val group = Group("groupId", Instant.now(), "groupName", null)

        whenever(entityManager.find(Group::class.java, "groupId")).thenReturn(group)

        val e = assertThrows<EntityAssociationDoesNotExistException> {
            groupWriter.removeRoleFromGroup(removeRoleFromGroupRequest, requestUserId)
        }

        assertEquals("Role 'roleId' is not associated with Group 'groupId'.", e.message)
    }

    @Test
    fun `remove role from group successfully persists change to group and removes association and writes audit log`() {
        val removeRoleFromGroupRequest = RemoveRoleFromGroupRequest().apply {
            groupId = "groupId"
            roleId = "roleId"
        }
        val group = Group("groupId", Instant.now(), "groupName", null)
        val role = Role("roleId", Instant.now(), "roleName", null)
        val assoc = RoleGroupAssociation("id", role, group, Instant.now())
        group.roleGroupAssociations.add(assoc)

        whenever(entityManager.find(Group::class.java, "groupId")).thenReturn(group)
        whenever(entityManager.find(Role::class.java, "roleId")).thenReturn(role)

        groupWriter.removeRoleFromGroup(removeRoleFromGroupRequest, requestUserId)

        val groupCaptor = argumentCaptor<Group>()
        val auditCaptor = argumentCaptor<ChangeAudit>()

        inOrder(entityTransaction, entityManager) {
            verify(entityTransaction).begin()
            verify(entityManager, times(1)).merge(groupCaptor.capture())
            verify(entityManager, times(1)).persist(auditCaptor.capture())
            verify(entityTransaction).commit()
        }

        val persistedGroup = groupCaptor.firstValue
        assertNotNull(persistedGroup)
        assertEquals("groupName", persistedGroup.name)
        assertTrue(persistedGroup.roleGroupAssociations.none { it.role.id == "roleId" })

        val audit = auditCaptor.firstValue
        assertNotNull(audit)
        assertEquals(RestPermissionOperation.DELETE_ROLE_FROM_GROUP, audit.changeType)
        assertContains(audit.details, "Role 'roleId' unassigned from Group 'groupId' by '$requestUserId'. Removed RoleGroupAssociation")
    }

    @Test
    fun `delete a group successfully removes the group and writes audit log`() {
        val deleteGroupRequest = DeleteGroupRequest().apply {
            groupId = "groupId"
        }
        val group = Group("groupId", Instant.now(), "groupName", null)

        val query = mock<TypedQuery<Long>>()
        whenever(entityManager.createQuery(any(), eq(Long::class.java))).thenReturn(query)
        whenever(query.setParameter(any<String>(), any<String>())).thenReturn(query)
        whenever(query.singleResult).thenReturn(0)

        whenever(entityManager.find(Group::class.java, "groupId")).thenReturn(group)

        groupWriter.deleteGroup(deleteGroupRequest, requestUserId)

        val groupCaptor = argumentCaptor<Group>()
        val auditCaptor = argumentCaptor<ChangeAudit>()

        inOrder(entityTransaction, entityManager) {
            verify(entityTransaction).begin()
            verify(entityManager, times(1)).remove(groupCaptor.capture())
            verify(entityManager, times(1)).persist(auditCaptor.capture())
            verify(entityTransaction).commit()
        }

        val removedGroup = groupCaptor.firstValue
        assertNotNull(removedGroup)
        assertEquals("groupName", removedGroup.name)

        val audit = auditCaptor.firstValue
        assertNotNull(audit)
        assertEquals(RestPermissionOperation.GROUP_DELETE, audit.changeType)
        assertEquals("Group '${removedGroup.id}' deleted by '$requestUserId'.", audit.details)
    }

    @Test
    fun `delete a non empty group throws an exception`() {
        val deleteGroupRequest = DeleteGroupRequest().apply {
            groupId = "groupId"
        }
        val group = Group("groupId", Instant.now(), "groupName", null)
        val user = User("userId", Instant.now(), "userName", "login", true, null, null, null,group)
        val role = Role("roleId", Instant.now(), "roleName", null)
        val assoc = RoleGroupAssociation("id", role, group, Instant.now())
        group.roleGroupAssociations.add(assoc)

        val query = mock<TypedQuery<Long>>()
        whenever(entityManager.createQuery(any(), eq(Long::class.java))).thenReturn(query)
        whenever(query.setParameter(any<String>(), any<String>())).thenReturn(query)
        whenever(query.singleResult).thenReturn(1)

        whenever(entityManager.find(Group::class.java, "groupId")).thenReturn(group)
        whenever(entityManager.find(User::class.java, "groupId")).thenReturn(user)

        val e = assertThrows<IllegalEntityStateException> {
            groupWriter.deleteGroup(deleteGroupRequest, requestUserId)
        }

        assertEquals("Group 'groupId' must be empty. 1 subgroups and 1 users are associated with it.", e.message)
    }
}