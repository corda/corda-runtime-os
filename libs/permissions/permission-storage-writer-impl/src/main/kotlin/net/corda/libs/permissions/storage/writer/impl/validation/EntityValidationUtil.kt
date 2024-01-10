package net.corda.libs.permissions.storage.writer.impl.validation

import javax.persistence.EntityManager
import net.corda.libs.permissions.common.exception.EntityAlreadyExistsException
import net.corda.libs.permissions.common.exception.EntityAssociationAlreadyExistsException
import net.corda.libs.permissions.common.exception.EntityAssociationDoesNotExistException
import net.corda.libs.permissions.common.exception.EntityNotFoundException
import net.corda.permissions.model.Group
import net.corda.permissions.model.Role
import net.corda.permissions.model.RolePermissionAssociation
import net.corda.permissions.model.RoleUserAssociation
import net.corda.permissions.model.User

class EntityValidationUtil(private val entityManager: EntityManager) {

    fun <T : Any> requireEntityExists(type: Class<T>, id: Any): T {
        val value = entityManager.find(type, id)
        if (value == null) {
            throw EntityNotFoundException("${type.simpleName} '$id' not found.")
        } else {
            return value
        }
    }

    fun requireRoleAssociatedWithPermission(
        associations: Set<RolePermissionAssociation>,
        permissionId: String,
        roleId: String
    ): RolePermissionAssociation {
        return associations.find { it.permission.id == permissionId }
            ?: throw EntityAssociationDoesNotExistException("Permission '$permissionId' is not associated with Role '$roleId'.")
    }

    fun requirePermissionNotAssociatedWithRole(
        associations: Set<RolePermissionAssociation>,
        permissionId: String,
        roleId: String
    ) {
        if(associations.any { it.permission.id == permissionId }) {
            throw EntityAssociationAlreadyExistsException("Permission '$permissionId' is already associated with Role '$roleId'.")
        }
    }

    fun validateAndGetUniqueUser(loginName: String): User {
        val userList = entityManager
            .createQuery("FROM User WHERE loginName = :loginName", User::class.java)
            .setParameter("loginName", loginName)
            .resultList

        return userList.getOrNull(0) ?: throw EntityNotFoundException("User '$loginName' not found.")
    }

    fun validateAndGetUniqueRole(roleId: String): Role {
        return entityManager.find(Role::class.java, roleId) ?: throw EntityNotFoundException("Role '$roleId' not found.")
    }

    fun validateAndGetOptionalParentGroup(groupId: String?): Group? {
        return if (groupId != null) {
            requireEntityExists(Group::class.java, groupId)
        } else null
    }

    fun validateRoleNotAlreadyAssignedToUser(user: User, roleId: String) {
        if (user.roleUserAssociations.any { it.role.id == roleId }) {
            throw EntityAssociationAlreadyExistsException("Role '$roleId' is already associated with User '${user.loginName}'.")
        }
    }

    fun validateAndGetRoleAssociatedWithUser(user: User, roleId: String): RoleUserAssociation {
        val value = user.roleUserAssociations.singleOrNull { it.role.id == roleId }
        if (value == null) {
            throw EntityAssociationDoesNotExistException("Role '$roleId' is not associated with User '${user.loginName}'.")
        } else {
            return value
        }
    }

    fun validateUserDoesNotAlreadyExist(loginName: String) {
        val count = entityManager
            .createQuery("SELECT count(1) FROM User WHERE loginName = :loginName")
            .setParameter("loginName", loginName)
            .singleResult as Long

        if (count != 0L) {
            throw EntityAlreadyExistsException("User '$loginName' already exists.")
        }
    }
}