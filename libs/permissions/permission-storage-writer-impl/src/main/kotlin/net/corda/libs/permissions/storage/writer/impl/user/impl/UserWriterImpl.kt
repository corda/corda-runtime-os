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

            require(countUsersWithLoginName(entityManager, request.loginName) == 0L) {
                "Failed to create user '${request.loginName}' as they already exist."
            }

            val parentGroup = if (request.parentGroupId != null) {
                requireNotNull(entityManager.find(Group::class.java, request.parentGroupId)) {
                    "Failed to create user '${request.loginName}'. The specified parent group '${request.parentGroupId}' does not exist."
                }
            } else {
                null
            }

            val user = persistNewUser(request, parentGroup, entityManager, requestUserId, loginName)
            user.toAvroUser()
        }
    }

    override fun addRoleToUser(request: AddRoleToUserRequest, requestUserId: String): net.corda.data.permissions.User {
        log.debug { "Received request to add Role ${request.roleId} to User ${request.loginName}" }
        return entityManagerFactory.transaction { entityManager ->

            val user = validateAndGetUniqueUser(entityManager, request.loginName)
            val role = validateAndGetUniqueRole(entityManager, request.roleId)
            validateRoleNotAlreadyAssignedToUser(user, role)

            val resultUser = persistUserRoleAssociation(entityManager, requestUserId, user, role)

            resultUser.toAvroUser()
        }
    }

    override fun removeRoleFromUser(request: RemoveRoleFromUserRequest, requestUserId: String): net.corda.data.permissions.User {
        log.debug { "Received request to remove Role ${request.roleId} from User ${request.loginName}" }
        return entityManagerFactory.transaction { entityManager ->

            val user = validateAndGetUniqueUser(entityManager, request.loginName)
            val role = validateAndGetUniqueRole(entityManager, request.roleId)
            val association = validateAndGetRoleAssociatedWithUser(user, role)

            val resultUser = removeUserRoleAssociation(entityManager, requestUserId, user, role, association)

            resultUser.toAvroUser()
        }
    }

    private fun countUsersWithLoginName(entityManager: EntityManager, loginName: String): Long {
        return entityManager
            .createQuery("SELECT count(1) FROM User WHERE loginName = :loginName")
            .setParameter("loginName", loginName)
            .singleResult as Long
    }

    private fun validateAndGetUniqueUser(entityManager: EntityManager, loginName: String): User {
        val users = entityManager
            .createQuery("FROM User WHERE loginName = :loginName", User::class.java)
            .setParameter("loginName", loginName)
            .resultList

        require(users.size == 1) { "User $loginName does not exist." }

        return users.first()
    }

    private fun validateAndGetUniqueRole(entityManager: EntityManager, roleId: String): Role {
        val roles = entityManager
            .createQuery("FROM Role WHERE id = :id", Role::class.java)
            .setParameter("id", roleId)
            .resultList

        require(roles.size == 1) { "Role '$roleId' does not exist." }

        return roles.first()
    }

    private fun validateRoleNotAlreadyAssignedToUser(user: User, role: Role) {
        require(user.roleUserAssociations.none { it.role == role }) {
            "Role ${role.name} is already associated with User ${user.loginName}."
        }
    }

    private fun validateAndGetRoleAssociatedWithUser(user: User, role: Role): RoleUserAssociation {
        return requireNotNull(user.roleUserAssociations.singleOrNull { it.role == role }) {
            "Role '${role.name}' is not associated with User '${user.loginName}'."
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
        user.version = 0

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
            details = "Role '${role.id}' assigned to User '${user.id}' by '$requestUserId'."
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
        role: Role,
        association: RoleUserAssociation
    ): User {
        val updateTimestamp = Instant.now()
        val changeAudit = ChangeAudit(
            id = UUID.randomUUID().toString(),
            updateTimestamp = updateTimestamp,
            actorUser = requestUserId,
            changeType = RPCPermissionOperation.REMOVE_ROLE_FROM_USER,
            details = "Role '${role.id}' unassigned from User '${user.id}' by '$requestUserId'."
        )
        user.roleUserAssociations.remove(association)

        // merging user cascades and remove the association.
        entityManager.remove(association)
        entityManager.merge(user)
        entityManager.persist(changeAudit)

        return user
    }
}