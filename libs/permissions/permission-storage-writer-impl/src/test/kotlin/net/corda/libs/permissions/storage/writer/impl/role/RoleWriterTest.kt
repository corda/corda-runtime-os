package net.corda.libs.permissions.storage.writer.impl.role

import javax.persistence.EntityManager
import javax.persistence.EntityManagerFactory
import javax.persistence.EntityTransaction
import javax.persistence.Query
import net.corda.data.permissions.management.role.CreateRoleRequest
import net.corda.libs.permissions.storage.writer.impl.role.impl.RoleWriterImpl
import net.corda.permissions.model.Group
import net.corda.permissions.model.Role
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

class RoleWriterTest {

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
    fun `create a role checks role doesn't already exist, if it does, throw illegal argument exception`() {

        whenever(entityManager.createQuery(any<String>())).thenReturn(query)
        whenever(query.setParameter(eq("roleName"), eq("role1"))).thenReturn(query)
        whenever(query.singleResult).thenReturn(1L)

        val e = assertThrows<IllegalArgumentException> {
            roleWriter.createRole(createRoleRequest)
        }

        verify(entityTransaction).begin()
        assertEquals("Failed to create new role: role1 as they already exist.", e.message)
    }

    @Test
    fun `create a role checks groupVisibility group ID and throws illegal argument exception if does not exist`() {

        whenever(entityManager.createQuery(any<String>())).thenReturn(query)
        whenever(query.setParameter(any<String>(), any())).thenReturn(query)
        whenever(query.singleResult).thenReturn(0L)

        whenever(entityManager.find(eq(Group::class.java), eq("group1"))).thenReturn(null)

        val e = assertThrows<IllegalArgumentException> {
            roleWriter.createRole(createRoleRequestWithGroupVis)
        }

        verify(entityTransaction).begin()
        assertEquals("Failed to create new Role: role2 as the specified group visibility: group1 does not exist.", e.message)
    }

    @Test
    fun `create a role without groupVisibility successfully persists the role setting groupVisibility to null`() {

        val roleCapture = argumentCaptor<Role>()

        whenever(entityManager.createQuery(any<String>())).thenReturn(query)
        whenever(query.setParameter(eq("roleName"), eq("role1"))).thenReturn(query)
        whenever(query.singleResult).thenReturn(0L)

        roleWriter.createRole(createRoleRequest)

        verify(entityManager).persist(roleCapture.capture())
        verify(entityTransaction).begin()
        verify(entityTransaction).commit()

        val persistedRole = roleCapture.firstValue
        assertNotNull(persistedRole)
        assertNotNull(persistedRole.id)
        assertNotNull(persistedRole.updateTimestamp)
        assertEquals("role1", persistedRole.name)
        assertNull(persistedRole.groupVisibility)
    }

    @Test
    fun `create a role with groupVisibility successfully persists the role setting groupVisibility`() {

        val roleCapture = argumentCaptor<Role>()

        whenever(entityManager.createQuery(any<String>())).thenReturn(query)
        whenever(query.setParameter(any<String>(), any())).thenReturn(query)
        whenever(query.singleResult).thenReturn(0L)

        whenever(entityManager.find(eq(Group::class.java), eq("group1"))).thenReturn(group)

        roleWriter.createRole(createRoleRequestWithGroupVis)

        verify(entityManager).persist(roleCapture.capture())
        verify(entityTransaction).begin()
        verify(entityTransaction).commit()

        val persistedRole = roleCapture.firstValue
        assertNotNull(persistedRole)
        assertNotNull(persistedRole.id)
        assertNotNull(persistedRole.updateTimestamp)
        assertEquals("role2", persistedRole.name)
        assertEquals(group, persistedRole.groupVisibility)
    }
}