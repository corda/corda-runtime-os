package net.corda.libs.permissions.storage.writer.impl

import net.corda.data.permissions.management.PermissionManagementRequest
import net.corda.data.permissions.management.user.CreateUserRequest
import net.corda.messaging.api.processor.RPCResponderProcessor
import net.corda.permissions.model.Group
import net.corda.permissions.model.User
import net.corda.v5.base.util.contextLogger
import net.corda.v5.base.util.debug
import java.time.Instant
import java.util.UUID
import java.util.concurrent.CompletableFuture
import javax.persistence.EntityManagerFactory

class PermissionStorageWriterProcessor(private val entityManagerFactory: EntityManagerFactory) :
    RPCResponderProcessor<PermissionManagementRequest, Unit> {

    private companion object {
        val log = contextLogger()
    }

    override fun onNext(request: PermissionManagementRequest, respFuture: CompletableFuture<Unit>) {
        try {
            when (val permissionRequest = request.request) {
                is CreateUserRequest -> createUser(permissionRequest)
                else -> throw IllegalArgumentException("Received invalid permission request type")
            }
            respFuture.complete(Unit)
        } catch (e: Exception) {
            log.warn(e.message)
            respFuture.completeExceptionally(e)
        }
    }

    private fun createUser(request: CreateUserRequest) {
        val loginName = request.loginName

        log.debug { "Received request to create new user: $loginName" }

        val entityManager = entityManagerFactory.createEntityManager()

        try {
            entityManager.transaction.begin()

            val result = entityManager
                .createQuery("SELECT count(1) from users where users.loginName = :loginName")
                .setParameter("loginName", loginName)
                .singleResult as Int

            require(result == 0) { "Failed to create new user: $loginName as they already exist" }

            val parentGroup = if (request.parentGroupId != null) {
                requireNotNull(entityManager.find(Group::class.java, request.parentGroupId)) {
                    "Failed to create new user: $loginName as the specified parent group: ${request.parentGroupId} does not exist"
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

            entityManager.persist(user)
            entityManager.transaction.commit()

            log.info("Successfully created new user: $loginName")
        } finally {
            entityManager.close()
        }
    }
}