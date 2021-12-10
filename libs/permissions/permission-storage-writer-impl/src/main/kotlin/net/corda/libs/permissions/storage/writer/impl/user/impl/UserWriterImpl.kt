package net.corda.libs.permissions.storage.writer.impl.user.impl

import java.time.Instant
import java.util.UUID
import javax.persistence.EntityManager
import javax.persistence.EntityManagerFactory
import net.corda.data.permissions.management.user.CreateUserRequest
import net.corda.libs.permissions.storage.writer.impl.common.toAvroUser
import net.corda.libs.permissions.storage.writer.impl.user.UserWriter
import net.corda.permissions.model.Group
import net.corda.permissions.model.User
import net.corda.v5.base.util.contextLogger
import net.corda.v5.base.util.debug

class UserWriterImpl(
    private val entityManagerFactory: EntityManagerFactory
) : UserWriter {

    private companion object {
        val log = contextLogger()
    }

    override fun createUser(request: CreateUserRequest): net.corda.data.permissions.User {
        val loginName = request.loginName

        log.debug { "Received request to create new user: $loginName" }

        val entityManager = entityManagerFactory.createEntityManager()

        return try {
            entityManager.transaction.begin()

            requireNewUser(entityManager, loginName)

            val parentGroup = if (request.parentGroupId != null) {
                requireNotNull(entityManager.find(Group::class.java, request.parentGroupId)) {
                    "Failed to create new user: $loginName as the specified parent group: ${request.parentGroupId} does not exist."
                }
            } else {
                null
            }

            val user = User(
                id = UUID.randomUUID().toString(),
                fullName = request.fullName,
                loginName = request.loginName,
                enabled = request.enabled,
                saltValue = request.saltValue,
                hashedPassword = request.initialHashedPassword,
                passwordExpiry = request.passwordExpiry,
                parentGroup = parentGroup,
                updateTimestamp = Instant.now()
            )
            user.version = 0

            entityManager.persist(user)
            entityManager.transaction.commit()

            log.info("Successfully created new user: $loginName.")

            user.toAvroUser()
        } finally {
            entityManager.close()
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