package net.corda.libs.permissions.storage.writer.impl.user

import java.time.Instant
import java.util.concurrent.atomic.AtomicBoolean
import javax.persistence.EntityManager
import javax.persistence.EntityManagerFactory
import javax.persistence.EntityTransaction
import javax.persistence.Query
import javax.persistence.TypedQuery
import net.corda.data.permissions.management.user.AddRoleToUserRequest
import net.corda.data.permissions.management.user.CreateUserRequest
import net.corda.data.permissions.management.user.RemoveRoleFromUserRequest
import net.corda.libs.permissions.common.exception.EntityAlreadyExistsException
import net.corda.libs.permissions.common.exception.EntityAssociationAlreadyExistsException
import net.corda.libs.permissions.common.exception.EntityAssociationDoesNotExistException
import net.corda.libs.permissions.common.exception.EntityNotFoundException
import net.corda.libs.permissions.storage.writer.impl.user.impl.UserWriterImpl
import net.corda.permissions.model.ChangeAudit
import net.corda.permissions.model.Group
import net.corda.permissions.model.RESTPermissionOperation
import net.corda.permissions.model.Role
import net.corda.permissions.model.RoleUserAssociation
import net.corda.permissions.model.User
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
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

internal class UserWriterImplTest {

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

    private val now = Instant.now()
    private val user = User("userId1", now, "user", "userLogin1", true, null, null, null, null)
    private val role = Role("role1", now, "roleName1", null)

    private val requestUserId = "requestUserId"
    private val entityTransaction = mock<EntityTransaction>()
    private val entityManager = mock<EntityManager>()
    private val entityManagerFactory = mock<EntityManagerFactory>()
    private val query = mock<Query>()
    private val userQuery = mock<TypedQuery<User>>()
    private val group = mock<Group>()

    private val userWriter = UserWriterImpl(entityManagerFactory)

    @BeforeEach
    fun setUp() {
        whenever(entityManager.transaction).thenReturn(entityTransaction)
        whenever(entityManagerFactory.createEntityManager()).thenReturn(entityManager)
        val rollBack = AtomicBoolean(false)
        whenever(entityTransaction.rollbackOnly).then { rollBack.get() }
    }

    @Test
    fun `receiving CreateUserRequest when a user with the same login name already exists completes exceptionally`() {

        whenever(entityManager.createQuery(any<String>())).thenReturn(query)
        whenever(query.setParameter(eq("loginName"), eq("lankydan"))).thenReturn(query)
        whenever(query.singleResult).thenReturn(1L)

        val e = assertThrows<EntityAlreadyExistsException> {
            userWriter.createUser(createUserRequest, requestUserId)
        }

        verify(entityTransaction).begin()
        assertEquals("User 'lankydan' already exists.", e.message)
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
        assertEquals(RESTPermissionOperation.USER_INSERT, audit.changeType)
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
        assertEquals(RESTPermissionOperation.USER_INSERT, audit.changeType)
        assertEquals(requestUserId, audit.actorUser)
    }

    @Test
    fun `add role to user fails when user does not exist`() {
        whenever(entityManager.createQuery(any(), eq(User::class.java))).thenReturn(userQuery)
        whenever(userQuery.setParameter("loginName", "userLogin1")).thenReturn(userQuery)
        whenever(userQuery.resultList).thenReturn(emptyList<User>())

        val e = assertThrows<EntityNotFoundException> {
            userWriter.addRoleToUser(AddRoleToUserRequest("userLogin1", "role1"), "requestUserId")
        }

        assertEquals("User 'userLogin1' not found.", e.message)
    }

    @Test
    fun `add role to user fails when role does not exist`() {
        whenever(entityManager.createQuery(any(), eq(User::class.java))).thenReturn(userQuery)
        whenever(userQuery.setParameter("loginName", "userLogin1")).thenReturn(userQuery)
        whenever(userQuery.resultList).thenReturn(listOf(user))

        whenever(entityManager.find(Role::class.java, "role1")).thenReturn(null)

        val e = assertThrows<EntityNotFoundException> {
            userWriter.addRoleToUser(AddRoleToUserRequest("userLogin1", "role1"), "requestUserId")
        }

        assertEquals("Role 'role1' not found.", e.message)
    }

    @Test
    fun `add role to user fails when role is already assigned to user`() {
        whenever(entityManager.createQuery(any(), eq(User::class.java))).thenReturn(userQuery)
        whenever(userQuery.setParameter("loginName", "userLogin1")).thenReturn(userQuery)
        whenever(userQuery.resultList).thenReturn(listOf(user))

        whenever(entityManager.find(Role::class.java, "role1")).thenReturn(role)

        user.roleUserAssociations.add(RoleUserAssociation("assoc1", role, user, Instant.now()))

        val e = assertThrows<EntityAssociationAlreadyExistsException> {
            userWriter.addRoleToUser(AddRoleToUserRequest("userLogin1", "role1"), "requestUserId")
        }

        assertEquals("Role 'role1' is already associated with User 'userLogin1'.", e.message)
    }

    @Test
    fun `add role to user successfully persists change to user and audit log`() {
        whenever(entityManager.createQuery(any(), eq(User::class.java))).thenReturn(userQuery)
        whenever(userQuery.setParameter("loginName", "userLogin1")).thenReturn(userQuery)
        whenever(userQuery.resultList).thenReturn(listOf(user))

        whenever(entityManager.find(Role::class.java, "role1")).thenReturn(role)

        val avroUser = userWriter.addRoleToUser(AddRoleToUserRequest("userLogin1", "role1"), "requestUserId")

        val capture = argumentCaptor<Any>()

        inOrder(entityTransaction, entityManager) {
            verify(entityTransaction).begin()
            verify(entityManager, times(1)).merge(capture.capture())
            verify(entityManager, times(1)).persist(capture.capture())
            verify(entityTransaction).commit()
        }

        assertEquals(2, capture.allValues.size)
        val persistedUser = capture.firstValue as User
        assertEquals(user, persistedUser)

        assertEquals(1, persistedUser.roleUserAssociations.size)
        val persistedAssociation = persistedUser.roleUserAssociations.first()
        assertEquals(role, persistedAssociation.role)
        assertEquals(user, persistedAssociation.user)

        val audit = capture.secondValue as ChangeAudit
        assertEquals(RESTPermissionOperation.ADD_ROLE_TO_USER, audit.changeType)
        assertEquals(
            "Role 'role1' assigned to User 'userLogin1' by 'requestUserId'. Created RoleUserAssociation '${persistedAssociation.id}'.",
            audit.details
        )
        assertEquals("requestUserId", audit.actorUser)

        assertEquals(user.id, avroUser.id)
        assertEquals(1, avroUser.roleAssociations.size)
        assertEquals(role.id, avroUser.roleAssociations.first().roleId)
    }

    @Test
    fun `remove role from user fails when user does not exist`() {
        whenever(entityManager.createQuery(any(), eq(User::class.java))).thenReturn(userQuery)
        whenever(userQuery.setParameter("loginName", "userLogin1")).thenReturn(userQuery)
        whenever(userQuery.resultList).thenReturn(emptyList<User>())

        val e = assertThrows<EntityNotFoundException> {
            userWriter.removeRoleFromUser(RemoveRoleFromUserRequest("userLogin1", "role1"), "requestUserId")
        }

        assertEquals("User 'userLogin1' not found.", e.message)
    }

    @Test
    fun `remove role from user fails when role is not assigned to user`() {
        whenever(entityManager.createQuery(any(), eq(User::class.java))).thenReturn(userQuery)
        whenever(userQuery.setParameter("loginName", "userLogin1")).thenReturn(userQuery)
        whenever(userQuery.resultList).thenReturn(listOf(user))

        val e = assertThrows<EntityAssociationDoesNotExistException> {
            userWriter.removeRoleFromUser(RemoveRoleFromUserRequest("userLogin1", "role1"), "requestUserId")
        }

        assertEquals("Role 'role1' is not associated with User 'userLogin1'.", e.message)
    }

    @Test
    fun `remove role from user successfully persists change to user and removes association and writes audit log`() {

        whenever(entityManager.createQuery(any(), eq(User::class.java))).thenReturn(userQuery)
        whenever(userQuery.setParameter("loginName", "userLogin1")).thenReturn(userQuery)
        whenever(userQuery.resultList).thenReturn(listOf(user))

        val assoc = RoleUserAssociation("assoc1", role, user, Instant.now())
        user.roleUserAssociations.add(assoc)

        val avroUser = userWriter.removeRoleFromUser(RemoveRoleFromUserRequest("userLogin1", "role1"), "requestUserId")

        val capture = argumentCaptor<Any>()

        inOrder(entityTransaction, entityManager) {
            verify(entityTransaction).begin()
            verify(entityManager, times(1)).remove(capture.capture())
            verify(entityManager, times(1)).merge(capture.capture())
            verify(entityManager, times(1)).persist(capture.capture())
            verify(entityTransaction).commit()
        }

        assertEquals(3, capture.allValues.size)
        val removedAssociation = capture.firstValue as RoleUserAssociation
        assertEquals(assoc, removedAssociation)

        val persistedUser = capture.secondValue as User
        assertEquals(user, persistedUser)
        assertEquals(0, persistedUser.roleUserAssociations.size)

        val audit = capture.thirdValue as ChangeAudit
        assertEquals(RESTPermissionOperation.DELETE_ROLE_FROM_USER, audit.changeType)
        assertEquals(
            "Role 'role1' unassigned from User 'userLogin1' by 'requestUserId'. Removed RoleUserAssociation 'assoc1'.",
            audit.details
        )
        assertEquals("requestUserId", audit.actorUser)

        assertEquals(user.id, avroUser.id)
        assertEquals(0, avroUser.roleAssociations.size)
    }
}