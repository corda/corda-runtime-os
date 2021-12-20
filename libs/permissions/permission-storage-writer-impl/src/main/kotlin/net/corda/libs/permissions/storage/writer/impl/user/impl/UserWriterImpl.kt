package net.corda.libs.permissions.storage.writer.impl.user.impl

import java.time.Instant
import java.util.UUID
import javax.persistence.EntityManager
import javax.persistence.EntityManagerFactory
import net.corda.data.permissions.management.user.CreateUserRequest
import net.corda.libs.permissions.storage.common.converter.toAvroUser
import net.corda.libs.permissions.storage.writer.impl.user.UserWriter
import net.corda.orm.utils.transaction
import net.corda.permissions.model.ChangeAudit
import net.corda.permissions.model.Group
import net.corda.permissions.model.RPCPermissionOperation
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
            requireNewUser(entityManager, loginName)

            val parentGroup = if (request.parentGroupId != null) {
                requireNotNull(entityManager.find(Group::class.java, request.parentGroupId)) {
                    "Failed to create new user: $loginName as the specified parent group: ${request.parentGroupId} does not exist."
                }
            } else {
                null
            }

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

            user.toAvroUser()
        }
    }

    private fun requireNewUser(entityManager: EntityManager, loginName: String) {
        val result = entityManager
            .createQuery("SELECT count(1) FROM User WHERE loginName = :loginName")
            .setParameter("loginName", loginName)
            .singleResult as Long

        require(result == 0L) { "Failed to create new user: $loginName as they already exist." }
    }
}