package net.corda.libs.permissions.storage.writer.impl.role

import net.corda.data.permissions.management.role.AddPermissionToRoleRequest
import javax.persistence.EntityManager
import javax.persistence.EntityManagerFactory
import javax.persistence.EntityTransaction
import javax.persistence.Query
import net.corda.data.permissions.management.role.CreateRoleRequest
import net.corda.libs.permissions.storage.writer.impl.role.impl.RoleWriterImpl
import net.corda.permissions.model.ChangeAudit
import net.corda.permissions.model.Group
import net.corda.permissions.model.Permission
import net.corda.permissions.model.PermissionType
import net.corda.permissions.model.RPCPermissionOperation
import net.corda.permissions.model.Role
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.eq
import org.mockito.kotlin.inOrder
import org.mockito.kotlin.mock
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.time.Instant

class RoleWriterTest {

    private val requestUserId = "requestor"
    private val createRoleRequest = CreateRoleRequest("role1", null)
    private val createRoleRequestWithGroupVis = CreateRoleRequest("role2", "group1")

    private val entityTransaction = mock<EntityTransaction>()
    private val entityManager = mock<EntityManager>()
    private val entityManagerFactory = mock<EntityManagerFactory>()
    private val query = mock<Query>()
    private val group = mock<Group>()

    private val roleWriter = RoleWriterImpl(entityManagerFactory)

    @BeforeEach
    fun setUp() {
        whenever(entityManager.transaction).thenReturn(entityTransaction)
        whenever(entityManagerFactory.createEntityManager()).thenReturn(entityManager)
    }

    @Test
    fun `create a role checks groupVisibility group ID and throws illegal argument exception if does not exist`() {

        whenever(entityManager.createQuery(any<String>())).thenReturn(query)
        whenever(query.setParameter(any<String>(), any())).thenReturn(query)
        whenever(query.singleResult).thenReturn(0L)

        whenever(entityManager.find(eq(Group::class.java), eq("group1"))).thenReturn(null)

        val e = assertThrows<IllegalArgumentException> {
            roleWriter.createRole(createRoleRequestWithGroupVis, requestUserId)
        }

        verify(entityTransaction).begin()
        assertEquals("Failed to create new Role: role2 as the specified group visibility: group1 does not exist.", e.message)
    }

    @Test
    fun `create a role without groupVisibility successfully persists the role and audit log`() {
        val capture = argumentCaptor<Any>()

        whenever(entityManager.createQuery(any<String>())).thenReturn(query)
        whenever(query.setParameter(eq("roleName"), eq("role1"))).thenReturn(query)
        whenever(query.singleResult).thenReturn(0L)

        roleWriter.createRole(createRoleRequest, requestUserId)

        inOrder(entityTransaction, entityManager) {
            verify(entityTransaction).begin()
            verify(entityManager, times(2)).persist(capture.capture())
            verify(entityTransaction).commit()
        }

        val persistedRole = capture.firstValue as Role
        assertNotNull(persistedRole)
        assertNotNull(persistedRole.id)
        assertNotNull(persistedRole.updateTimestamp)
        assertEquals("role1", persistedRole.name)
        assertNull(persistedRole.groupVisibility)

        val audit = capture.secondValue as ChangeAudit
        assertNotNull(audit)
        assertEquals(RPCPermissionOperation.ROLE_INSERT, audit.changeType)
        assertEquals(requestUserId, audit.actorUser)
    }

    @Test
    fun `create a role with groupVisibility successfully persists the role and writes audit log`() {
        val capture = argumentCaptor<Any>()

        whenever(entityManager.createQuery(any<String>())).thenReturn(query)
        whenever(query.setParameter(any<String>(), any())).thenReturn(query)
        whenever(query.singleResult).thenReturn(0L)

        whenever(entityManager.find(eq(Group::class.java), eq("group1"))).thenReturn(group)

        roleWriter.createRole(createRoleRequestWithGroupVis, requestUserId)

        inOrder(entityTransaction, entityManager) {
            verify(entityTransaction).begin()
            verify(entityManager, times(2)).persist(capture.capture())
            verify(entityTransaction).commit()
        }

        val persistedRole = capture.firstValue as Role
        assertNotNull(persistedRole)
        assertNotNull(persistedRole.id)
        assertNotNull(persistedRole.updateTimestamp)
        assertEquals("role2", persistedRole.name)
        assertEquals(group, persistedRole.groupVisibility)

        val audit = capture.secondValue as ChangeAudit
        assertNotNull(audit)
        assertEquals(RPCPermissionOperation.ROLE_INSERT, audit.changeType)
        assertEquals(requestUserId, audit.actorUser)
    }

    @Test
    fun `add permission to role success`() {
        val roleId = "roleId"
        val permId = "permId"

        val role = Role(roleId, Instant.now(), "role", null)
        val permission = Permission(permId, Instant.now(), null, null,
            PermissionType.ALLOW, "permString")

        whenever(entityManager.find(Role::class.java, roleId)).thenReturn(role)
        whenever(entityManager.find(Permission::class.java, permId)).thenReturn(permission)

        val request = AddPermissionToRoleRequest(roleId, permId)
        val avroRole = roleWriter.addPermissionToRole(request, requestUserId)

        val capture = argumentCaptor<Any>()
        inOrder(entityTransaction, entityManager) {
            verify(entityTransaction).begin()
            verify(entityManager).merge(capture.capture())
            verify(entityManager).persist(capture.capture())
            verify(entityTransaction).commit()
        }

        assertEquals(roleId, avroRole.id)

        val persistedRole = capture.firstValue as Role
        assertSame(role, persistedRole)

        assertThat(persistedRole.rolePermAssociations).hasSize(1).allMatch { it.permission === permission }

        val audit = capture.secondValue as ChangeAudit
        assertNotNull(audit)
        assertEquals(RPCPermissionOperation.ADD_PERMISSION_TO_ROLE, audit.changeType)
        assertEquals(requestUserId, audit.actorUser)
    }
}