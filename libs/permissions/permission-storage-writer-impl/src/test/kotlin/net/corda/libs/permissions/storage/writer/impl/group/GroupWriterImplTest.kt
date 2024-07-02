package net.corda.libs.permissions.storage.writer.impl.group

import net.corda.data.permissions.management.group.ChangeGroupParentIdRequest
import net.corda.data.permissions.management.group.CreateGroupRequest
import net.corda.libs.permissions.common.exception.EntityNotFoundException
import net.corda.libs.permissions.storage.writer.impl.group.impl.GroupWriterImpl
import net.corda.permissions.model.ChangeAudit
import net.corda.permissions.model.Group
import net.corda.permissions.model.RestPermissionOperation
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.mockito.kotlin.*
import java.time.Instant
import javax.persistence.EntityManager
import javax.persistence.EntityManagerFactory
import javax.persistence.EntityTransaction

class GroupWriterImplTest {

    private val entityManager = mock<EntityManager>()
    private val entityManagerFactory = mock<EntityManagerFactory> {
        on { createEntityManager() }.thenReturn(entityManager)
    }
    private val groupWriter = GroupWriterImpl(entityManagerFactory)

    private val requestUserId = "requestUserId"


    @Test
    fun `create a group with no parent specified successfully persists the group`() {
        val createGroupRequest = CreateGroupRequest().apply {
            groupName = "group1"
        }
        val entityTransaction = mock<EntityTransaction>()
        whenever(entityManager.transaction).thenReturn(entityTransaction)

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
        assertEquals("group1", persistedGroup.name)
        assertNull(persistedGroup.parentGroup)

        val audit = auditCaptor.firstValue
        assertNotNull(audit)
        assertEquals(RestPermissionOperation.GROUP_INSERT, audit.changeType)
        assertEquals("Group '${persistedGroup.name}' created by '$requestUserId'.", audit.details)
    }

    @Test
    fun `create a group with a parent group specified persists a new group to the database`() {
        val createGroupRequest = CreateGroupRequest().apply {
            groupName = "group1"
            parentGroupId = "parent1"
        }
        val parentGroup = Group("parent1", Instant.now(), "parentGroup", null)
        val entityTransaction = mock<EntityTransaction>()

        whenever(entityManager.transaction).thenReturn(entityTransaction)
        whenever(entityManager.find(Group::class.java, "parent1")).thenReturn(parentGroup)

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
        assertEquals("group1", persistedGroup.name)
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

        val entityTransaction = mock<EntityTransaction>()
        whenever(entityManager.transaction).thenReturn(entityTransaction)
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

        val entityTransaction = mock<EntityTransaction>()
        whenever(entityManager.transaction).thenReturn(entityTransaction)
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
        val entityTransaction = mock<EntityTransaction>()

        whenever(entityManager.transaction).thenReturn(entityTransaction)
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
}