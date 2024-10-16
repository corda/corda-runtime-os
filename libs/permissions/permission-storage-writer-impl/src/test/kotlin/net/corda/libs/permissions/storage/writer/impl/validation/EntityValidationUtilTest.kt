package net.corda.libs.permissions.storage.writer.impl.validation

import net.corda.libs.permissions.common.exception.EntityAlreadyExistsException
import net.corda.libs.permissions.common.exception.EntityAssociationAlreadyExistsException
import net.corda.libs.permissions.common.exception.EntityAssociationDoesNotExistException
import net.corda.libs.permissions.common.exception.EntityNotFoundException
import net.corda.libs.permissions.common.exception.IllegalEntityStateException
import net.corda.permissions.model.Group
import net.corda.permissions.model.Permission
import net.corda.permissions.model.Role
import net.corda.permissions.model.RoleGroupAssociation
import net.corda.permissions.model.RolePermissionAssociation
import net.corda.permissions.model.RoleUserAssociation
import net.corda.permissions.model.User
import org.junit.jupiter.api.Assertions.assertDoesNotThrow
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import java.time.Instant
import javax.persistence.EntityManager
import javax.persistence.Query
import javax.persistence.TypedQuery

class EntityValidationUtilTest {

    private val entityManager = mock<EntityManager>()
    private val validator = EntityValidationUtil(entityManager)

    data class DummyClass(val id: String)

    @Test
    fun requireEntityExistsReturnsEntity() {
        whenever(entityManager.find(DummyClass::class.java, "id")).thenReturn(DummyClass("123"))

        val result = validator.requireEntityExists(DummyClass::class.java, "id")

        assertEquals(DummyClass("123"), result)
    }

    @Test
    fun requireEntityExistsThrowsCorrectException() {
        whenever(entityManager.find(DummyClass::class.java, "id")).thenReturn(null)

        assertThrows<EntityNotFoundException>("DummyClass 'id' not found.") {
            validator.requireEntityExists(DummyClass::class.java, "id")
        }
    }

    @Test
    fun requireRoleAssociatedWithPermissionThrowsCorrectException() {
        assertThrows<EntityAssociationDoesNotExistException>("Permission 'permid' is not associated with Role 'roleid'.") {
            validator.requireRoleAssociatedWithPermission(emptySet(), "permid", "roleid")
        }
    }

    @Test
    fun requireRoleAssociatedWithPermissionReturnsAssociation() {
        val role = mock<Role> {
            on { id }.thenReturn("roleid")
        }
        val perm = mock<Permission> {
            on { id }.thenReturn("permid")
        }
        val assoc = RolePermissionAssociation("id", role, perm, Instant.now())
        val associations = setOf(assoc)
        val result = validator.requireRoleAssociatedWithPermission(associations, "permid", "roleid")
        assertEquals(assoc, result)
    }

    @Test
    fun requirePermissionNotAssociatedWithRoleThrowsCorrectException() {
        val role = mock<Role> {
            on { id }.thenReturn("roleid")
        }
        val perm = mock<Permission> {
            on { id }.thenReturn("permid")
        }
        val associations = setOf(RolePermissionAssociation("id", role, perm, Instant.now()))

        assertThrows<EntityAssociationAlreadyExistsException>("Permission 'permid' is already associated with Role 'roleid'.") {
            validator.requirePermissionNotAssociatedWithRole(associations, "permid", "roleid")
        }
    }

    @Test
    fun validateAndGetUniqueUserThrowsCorrectException() {
        val query = mock<TypedQuery<User>>()

        whenever(entityManager.createQuery(any(), eq(User::class.java))).thenReturn(query)
        whenever(query.setParameter(eq("loginName"), eq("username"))).thenReturn(query)
        whenever(query.resultList).thenReturn(emptyList<User>())

        assertThrows<EntityNotFoundException>("User 'username' not found.") {
            validator.validateAndGetUniqueUser("username")
        }
    }

    @Test
    fun validateAndGetUniqueUserReturnsUser() {
        val user = mock<User>()
        val query = mock<TypedQuery<User>>()

        whenever(entityManager.createQuery(any(), eq(User::class.java))).thenReturn(query)
        whenever(query.setParameter(eq("loginName"), eq("username"))).thenReturn(query)
        whenever(query.resultList).thenReturn(listOf(user))

        val result = validator.validateAndGetUniqueUser("username")
        assertEquals(user, result)
    }

    @Test
    fun validateAndGetUniqueRoleThrowsCorrectException() {
        whenever(entityManager.find(Role::class.java, "roleid")).thenReturn(null)

        assertThrows<EntityNotFoundException>("Role 'roleid' not found.") {
            validator.validateAndGetUniqueRole("roleid")
        }
    }

    @Test
    fun validateAndGetUniqueRoleReturnsRole() {
        val role = mock<Role>()
        whenever(entityManager.find(Role::class.java, "roleid")).thenReturn(role)

        val result = validator.validateAndGetUniqueRole("roleid")
        assertEquals(role, result)
    }

    @Test
    fun `validateAndGetUniqueGroup returns group when it exists`() {
        val groupId = "group1"
        val group = mock<Group>()
        whenever(entityManager.find(Group::class.java, groupId)).thenReturn(group)

        val result = validator.validateAndGetUniqueGroup(groupId)

        assertEquals(group, result)
    }

    @Test
    fun `validateAndGetUniqueGroup throws exception when group does not exist`() {
        val groupId = "group1"
        whenever(entityManager.find(Group::class.java, groupId)).thenReturn(null)

        assertThrows<EntityNotFoundException>("Group '$groupId' not found.") {
            validator.validateAndGetUniqueGroup(groupId)
        }
    }

    @Test
    fun validateAndGetOptionalParentGroupReturnsNullWhenNoGroup() {
        assertNull(validator.validateAndGetOptionalParentGroup(null))
    }

    @Test
    fun validateAndGetOptionalParentGroupThrowsCorrectExceptionWhenGroupInvalid() {
        whenever(entityManager.find(eq(Group::class.java), eq("g1"))).thenReturn(null)

        assertThrows<EntityNotFoundException>("Group 'g1' not found.") {
            validator.validateAndGetOptionalParentGroup("g1")
        }
    }

    @Test
    fun validateAndGetOptionalParentGroupReturnsGroup() {
        val group = mock<Group>()
        whenever(entityManager.find(eq(Group::class.java), eq("g1"))).thenReturn(group)

        val result = validator.validateAndGetOptionalParentGroup("g1")
        assertEquals(group, result)
    }

    @Test
    fun `validateRoleNotAlreadyAssignedToGroup does not throw exception when role is not assigned to group`() {
        val group = mock<Group> {
            on { roleGroupAssociations }.thenReturn(mutableSetOf())
        }
        val roleId = "role1"

        assertDoesNotThrow {
            validator.validateRoleNotAlreadyAssignedToGroup(group, roleId)
        }
    }

    @Test
    fun `validateRoleNotAlreadyAssignedToGroup throws exception when role is already assigned to group`() {
        val roleId = "role1"
        val role = mock<Role> {
            on { id }.thenReturn(roleId)
        }
        val group = mock<Group> {
            on { id }.thenReturn("group1")
            on { roleGroupAssociations }.thenReturn(mutableSetOf(RoleGroupAssociation("id", role, this.mock, Instant.now())))
        }

        assertThrows<EntityAssociationAlreadyExistsException>("Role '$roleId' is already associated with Group '${group.id}'.") {
            validator.validateRoleNotAlreadyAssignedToGroup(group, roleId)
        }
    }

    @Test
    fun `validateAndGetRoleAssociatedWithGroup returns association when role is associated with group`() {
        val roleId = "role1"
        val role = mock<Role> {
            on { id }.thenReturn(roleId)
        }
        val group = mock<Group> {
            on { id }.thenReturn("group1")
            on { roleGroupAssociations }.thenReturn(mutableSetOf(RoleGroupAssociation("id", role, this.mock, Instant.now())))
        }

        val result = validator.validateAndGetRoleAssociatedWithGroup(group, roleId)

        assertEquals(roleId, result.role.id)
    }

    @Test
    fun `validateAndGetRoleAssociatedWithGroup throws exception when role is not associated with group`() {
        val group = mock<Group> {
            on { id }.thenReturn("group1")
            on { roleGroupAssociations }.thenReturn(mutableSetOf())
        }
        val roleId = "role1"

        assertThrows<EntityAssociationDoesNotExistException>("Role '$roleId' is not associated with Group '${group.id}'.") {
            validator.validateAndGetRoleAssociatedWithGroup(group, roleId)
        }
    }

    @Test
    fun `validateGroupIsEmpty does not throw exception when group is empty`() {
        val groupId = "group1"
        val group = mock<Group> {
            on { id }.thenReturn(groupId)
        }
        val query = mock<TypedQuery<Long>>()
        whenever(entityManager.createQuery(any(), eq(Long::class.javaObjectType))).thenReturn(query)
        whenever(query.setParameter("groupId", groupId)).thenReturn(query)
        whenever(query.singleResult).thenReturn(0L)

        assertDoesNotThrow {
            validator.validateGroupIsEmpty(group)
        }
    }

    @Test
    fun `validateGroupIsEmpty throws exception when group is not empty`() {
        val groupId = "group1"
        val group = mock<Group> {
            on { id }.thenReturn(groupId)
        }
        val query = mock<TypedQuery<Long>>()
        whenever(entityManager.createQuery(any(), eq(Long::class.javaObjectType))).thenReturn(query)
        whenever(query.setParameter("groupId", groupId)).thenReturn(query)
        whenever(query.singleResult).thenReturn(1L)

        assertThrows<IllegalEntityStateException>("Group '$groupId' is not empty. 1 subgroups and 1 users are associated with it.") {
            validator.validateGroupIsEmpty(group)
        }
    }

    @Test
    fun validateRoleNotAlreadyAssignedToUserThrowsCorrectException() {
        val role = mock<Role> {
            on { id }.thenReturn("r1")
        }
        val user = mock<User> {
            on { id }.thenReturn("u1")
        }
        whenever(user.roleUserAssociations).thenReturn(mutableSetOf(RoleUserAssociation("id", role, user, Instant.now())))

        assertThrows<EntityAssociationAlreadyExistsException>("Role 'r1' is already associated with User 'u1'.") {
            validator.validateRoleNotAlreadyAssignedToUser(user, "r1")
        }
    }

    @Test
    fun validateAndGetRoleAssociatedWithUserThrowsCorrectException() {
        val role = mock<Role> {
            on { id }.thenReturn("r2")
        }
        val user = mock<User> {
            on { id }.thenReturn("u1")
        }
        whenever(user.roleUserAssociations).thenReturn(mutableSetOf(RoleUserAssociation("id", role, user, Instant.now())))

        assertThrows<EntityAssociationDoesNotExistException>("Role 'r1' is not associated with User 'u1'.") {
            validator.validateAndGetRoleAssociatedWithUser(user, "r1")
        }
    }

    @Test
    fun validateAndGetRoleAssociatedWithUserReturnsAssoc() {
        val role = mock<Role> {
            on { id }.thenReturn("r1")
        }
        val user = mock<User> {
            on { id }.thenReturn("u1")
        }
        val assoc = RoleUserAssociation("id", role, user, Instant.now())
        whenever(user.roleUserAssociations).thenReturn(mutableSetOf(assoc))

        val result = validator.validateAndGetRoleAssociatedWithUser(user, "r1")
        assertEquals(assoc, result)
    }

    @Test
    fun validateUserDoesNotAlreadyExistThrowsCorrectException() {
        val query = mock<Query>()

        whenever(entityManager.createQuery(any<String>())).thenReturn(query)
        whenever(query.setParameter(eq("loginName"), eq("username"))).thenReturn(query)
        whenever(query.singleResult).thenReturn(1L)

        assertThrows<EntityAlreadyExistsException>("User 'username' already exists.") {
            validator.validateUserDoesNotAlreadyExist("username")
        }
    }
}
