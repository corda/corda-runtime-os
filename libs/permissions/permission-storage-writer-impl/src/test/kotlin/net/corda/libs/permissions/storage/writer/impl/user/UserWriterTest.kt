package net.corda.libs.permissions.storage.writer.impl.user

import javax.persistence.EntityManager
import javax.persistence.EntityManagerFactory
import javax.persistence.EntityTransaction
import javax.persistence.Query
import net.corda.data.permissions.management.user.CreateUserRequest
import net.corda.libs.permissions.storage.writer.impl.user.impl.UserWriterImpl
import net.corda.permissions.model.ChangeAudit
import net.corda.permissions.model.Group
import net.corda.permissions.model.RPCPermissionOperation
import net.corda.permissions.model.User
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.eq
import org.mockito.kotlin.inOrder
import org.mockito.kotlin.mock
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

internal class UserWriterTest {

    private val createUserRequest = CreateUserRequest().apply {
        fullName = "Dan Newton"
        loginName = "lankydan"
        enabled = true
    }

    private val createUserRequestWithParentGroup = CreateUserRequest().apply {
        fullName = "Dan Newton"
        loginName = "lankydan"
        enabled = true
        parentGroupId = "parent1"
    }

    private val requestUserId = "requestUserId"
    private val entityTransaction = mock<EntityTransaction>()
    private val entityManager = mock<EntityManager>()
    private val entityManagerFactory = mock<EntityManagerFactory>()
    private val query = mock<Query>()
    private val group = mock<Group>()

    private val userWriter = UserWriterImpl(entityManagerFactory)

    @BeforeEach
    fun setUp() {
        whenever(entityManager.transaction).thenReturn(entityTransaction)
        whenever(entityManagerFactory.createEntityManager()).thenReturn(entityManager)
    }

    @Test
    fun `receiving CreateUserRequest when a user with the same login name already exists completes exceptionally`() {

        whenever(entityManager.createQuery(any<String>())).thenReturn(query)
        whenever(query.setParameter(eq("loginName"), eq("lankydan"))).thenReturn(query)
        whenever(query.singleResult).thenReturn(1L)

        val e = org.junit.jupiter.api.assertThrows<IllegalArgumentException> {
            userWriter.createUser(createUserRequest, requestUserId)
        }

        verify(entityTransaction).begin()
        assertEquals("Failed to create new user 'lankydan' as they already exist.", e.message)
    }

    @Test
    fun `receiving CreateUserRequest specifying a parent group that exists persists a new user to the database`() {
        val capture = argumentCaptor<Any>()

        whenever(query.setParameter(any<String>(), any())).thenReturn(query)
        whenever(query.singleResult).thenReturn(0L)
        whenever(entityManager.createQuery(any<String>())).thenReturn(query)
        whenever(entityManager.find(eq(Group::class.java), eq("parent1"))).thenReturn(group)

        userWriter.createUser(createUserRequestWithParentGroup, requestUserId)

        inOrder(entityTransaction, entityManager) {
            verify(entityTransaction).begin()
            verify(entityManager, times(2)).persist(capture.capture())
            verify(entityTransaction).commit()
        }

        val persistedUser = capture.firstValue as User
        assertNotNull(persistedUser)
        assertNotNull(persistedUser.id)
        assertNotNull(persistedUser.updateTimestamp)
        assertEquals("Dan Newton", persistedUser.fullName)
        assertEquals("lankydan", persistedUser.loginName)
        assertNull(persistedUser.saltValue)
        assertNull(persistedUser.hashedPassword)
        assertNull(persistedUser.passwordExpiry)
        assertEquals(group, persistedUser.parentGroup)

        val audit = capture.secondValue as ChangeAudit
        assertNotNull(audit)
        assertEquals(RPCPermissionOperation.USER_INSERT, audit.changeType)
        assertEquals(requestUserId, audit.actorUser)
    }

    @Test
    fun `create a user successfully persists the user`() {
        val capture = argumentCaptor<Any>()

        whenever(entityManager.createQuery(any<String>())).thenReturn(query)
        whenever(query.setParameter(eq("loginName"), eq("lankydan"))).thenReturn(query)
        whenever(query.singleResult).thenReturn(0L)

        userWriter.createUser(createUserRequest, requestUserId)

        inOrder(entityTransaction, entityManager) {
            verify(entityTransaction).begin()
            verify(entityManager, times(2)).persist(capture.capture())
            verify(entityTransaction).commit()
        }

        val persistedUser = capture.firstValue as User
        assertNotNull(persistedUser)
        assertEquals("Dan Newton", persistedUser.fullName)
        assertEquals("lankydan", persistedUser.loginName)
        assertTrue(persistedUser.enabled)

        val audit = capture.secondValue as ChangeAudit
        assertNotNull(audit)
        assertEquals(RPCPermissionOperation.USER_INSERT, audit.changeType)
        assertEquals(requestUserId, audit.actorUser)
    }
}