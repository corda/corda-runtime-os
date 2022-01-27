package net.corda.libs.permissions.storage.writer.impl.user.impl

import java.time.Instant
import java.util.UUID
import javax.persistence.EntityManager
import javax.persistence.EntityManagerFactory
import net.corda.data.permissions.management.user.AddRoleToUserRequest
import net.corda.data.permissions.management.user.CreateUserRequest
import net.corda.data.permissions.management.user.RemoveRoleFromUserRequest
import net.corda.libs.permissions.storage.common.converter.toAvroUser
import net.corda.libs.permissions.storage.writer.impl.user.UserWriter
import net.corda.orm.utils.transaction
import net.corda.permissions.model.ChangeAudit
import net.corda.permissions.model.Group
import net.corda.permissions.model.RPCPermissionOperation
import net.corda.permissions.model.Role
import net.corda.permissions.model.RoleUserAssociation
import net.corda.permissions.model.User
import net.corda.v5.base.util.contextLogger
import net.corda.v5.base.util.debug
import net.corda.data.permissions.User as AvroUser

class UserWriterImpl(
    private val entityManagerFactory: EntityManagerFactory
) : UserWriter {

    private companion object {
        val log = contextLogger()
    }

    override fun createUser(request: CreateUserRequest, requestUserId: String): AvroUser {
        val loginName = request.loginName
        log.debug { "Received request to create new user: $loginName" }
        return entityManagerFactory.transaction { entityManager ->

            validateUserDoesNotAlreadyExist(entityManager, request)
            val parentGroup = validateAndGetOptionalParentGroup(request, entityManager)

            val user = persistNewUser(request, parentGroup, entityManager, requestUserId, loginName)
            user.toAvroUser()
        }
    }

    override fun addRoleToUser(request: AddRoleToUserRequest, requestUserId: String): AvroUser {
        log.debug { "Received request to add Role ${request.roleId} to User ${request.loginName}" }
        return entityManagerFactory.transaction { entityManager ->

            val user = validateAndGetUniqueUser(entityManager, request.loginName)
            val role = validateAndGetUniqueRole(entityManager, request.roleId)
            validateRoleNotAlreadyAssignedToUser(entityManager, user, request.roleId)

            val resultUser = persistUserRoleAssociation(entityManager, requestUserId, user, role)

            resultUser.toAvroUser()
        }
    }

    override fun removeRoleFromUser(request: RemoveRoleFromUserRequest, requestUserId: String): AvroUser {
        log.debug { "Received request to remove Role ${request.roleId} from User ${request.loginName}" }
        return entityManagerFactory.transaction { entityManager ->

            val user = validateAndGetUniqueUser(entityManager, request.loginName)
            val association = validateAndGetRoleAssociatedWithUser(entityManager, user, request.roleId)

            val resultUser = removeUserRoleAssociation(entityManager, requestUserId, user, request.roleId, association)

            resultUser.toAvroUser()
        }
    }

    private fun validateUserDoesNotAlreadyExist(entityManager: EntityManager, request: CreateUserRequest) {
        val count = entityManager
            .createQuery("SELECT count(1) FROM User WHERE loginName = :loginName")
            .setParameter("loginName", request.loginName)
            .singleResult as Long

        require(count == 0L) {
            entityManager.transaction.setRollbackOnly()
            "User with login '${request.loginName}' already exists."
        }
    }

    private fun validateAndGetOptionalParentGroup(request: CreateUserRequest, entityManager: EntityManager): Group? {
        val parentGroup = if (request.parentGroupId != null) {
            requireNotNull(entityManager.find(Group::class.java, request.parentGroupId)) {
                entityManager.transaction.setRollbackOnly()
                "The specified parent group '${request.parentGroupId}' does not exist."
            }
        } else {
            null
        }
        return parentGroup
    }

    private fun validateAndGetUniqueUser(entityManager: EntityManager, loginName: String): User {
        val users = entityManager
            .createQuery("FROM User WHERE loginName = :loginName", User::class.java)
            .setParameter("loginName", loginName)
            .resultList

        require(users.size == 1) {
            entityManager.transaction.setRollbackOnly()
            "User '$loginName' does not exist."
        }

        return users.first()
    }

    private fun validateAndGetUniqueRole(entityManager: EntityManager, roleId: String): Role {
        val role = entityManager.find(Role::class.java, roleId)

        requireNotNull(role) {
            entityManager.transaction.setRollbackOnly()
            "Role '$roleId' does not exist."
        }

        return role
    }

    private fun validateRoleNotAlreadyAssignedToUser(entityManager: EntityManager, user: User, roleId: String) {
        require(user.roleUserAssociations.none { it.role.id == roleId }) {
            entityManager.transaction.setRollbackOnly()
            "Role '$roleId' is already associated with User '${user.loginName}'."
        }
    }

    private fun validateAndGetRoleAssociatedWithUser(entityManager: EntityManager, user: User, roleId: String): RoleUserAssociation {
        return requireNotNull(user.roleUserAssociations.singleOrNull { it.role.id == roleId }) {
            entityManager.transaction.setRollbackOnly()
            "Role '$roleId' is not associated with User '${user.loginName}'."
        }
    }

    private fun persistNewUser(
        request: CreateUserRequest,
        parentGroup: Group?,
        entityManager: EntityManager,
        requestUserId: String,
        loginName: String
    ): User {
        val updateTimestamp = Instant.now()
        val user = User(
            id = UUID.randomUUID().toString(),
            fullName = request.fullName,
            loginName = request.loginName,
            enabled = request.enabled,
            saltValue = request.saltValue,
            hashedPassword = request.initialHashedPassword,
            passwordExpiry = request.passwordExpiry,
            parentGroup = parentGroup,
            updateTimestamp = updateTimestamp
        )

        entityManager.persist(user)

        val auditLog = ChangeAudit(
            id = UUID.randomUUID().toString(),
            updateTimestamp = updateTimestamp,
            actorUser = requestUserId,
            changeType = RPCPermissionOperation.USER_INSERT,
            details = "User '${user.loginName}' created by '$requestUserId'."
        )

        entityManager.persist(auditLog)

        log.info("Successfully created new user: $loginName.")
        return user
    }

    private fun persistUserRoleAssociation(entityManager: EntityManager, requestUserId: String, user: User, role: Role): User {
        val updateTimestamp = Instant.now()
        val association = RoleUserAssociation(UUID.randomUUID().toString(), role, user, updateTimestamp)
        val changeAudit = ChangeAudit(
            id = UUID.randomUUID().toString(),
            updateTimestamp = updateTimestamp,
            actorUser = requestUserId,
            changeType = RPCPermissionOperation.ADD_ROLE_TO_USER,
            details = "Role '${role.id}' assigned to User '${user.loginName}' by '$requestUserId'. " +
                    "Created RoleUserAssociation '${association.id}'."
        )

        user.roleUserAssociations.add(association)

        // merging user cascades and adds the association
        entityManager.merge(user)
        entityManager.persist(changeAudit)

        return user
    }

    private fun removeUserRoleAssociation(
        entityManager: EntityManager,
        requestUserId: String,
        user: User,
        roleId: String,
        association: RoleUserAssociation
    ): User {
        val updateTimestamp = Instant.now()
        val changeAudit = ChangeAudit(
            id = UUID.randomUUID().toString(),
            updateTimestamp = updateTimestamp,
            actorUser = requestUserId,
            changeType = RPCPermissionOperation.DELETE_ROLE_FROM_USER,
            details = "Role '$roleId' unassigned from User '${user.loginName}' by '$requestUserId'. " +
                    "Removed RoleUserAssociation '${association.id}'."
        )

        user.roleUserAssociations.remove(association)

        entityManager.remove(association)
        entityManager.merge(user)
        entityManager.persist(changeAudit)

        return user
    }
}