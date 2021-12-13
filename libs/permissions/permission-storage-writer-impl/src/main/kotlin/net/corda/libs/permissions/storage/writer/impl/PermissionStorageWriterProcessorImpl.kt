package net.corda.libs.permissions.storage.writer.impl

import net.corda.data.permissions.ChangeDetails
import net.corda.data.permissions.management.PermissionManagementRequest
import net.corda.data.permissions.management.PermissionManagementResponse
import net.corda.data.permissions.management.user.CreateUserRequest
import net.corda.libs.permissions.storage.writer.PermissionStorageWriterProcessor
import net.corda.orm.utils.commit
import net.corda.permissions.model.ChangeAudit
import net.corda.permissions.model.Group
import net.corda.permissions.model.RPCPermissionOperation
import net.corda.permissions.model.User
import net.corda.v5.base.util.contextLogger
import net.corda.v5.base.util.debug
import java.time.Instant
import java.util.UUID
import java.util.concurrent.CompletableFuture
import javax.persistence.EntityManager
import javax.persistence.EntityManagerFactory
import net.corda.data.permissions.User as AvroUser

class PermissionStorageWriterProcessorImpl(private val entityManagerFactory: EntityManagerFactory) : PermissionStorageWriterProcessor {

    private companion object {
        val log = contextLogger()
    }

    override fun onNext(request: PermissionManagementRequest, respFuture: CompletableFuture<PermissionManagementResponse>) {
        try {
            val response = when (val permissionRequest = request.request) {
                is CreateUserRequest -> createUser(permissionRequest, request.requestUserId)
                else -> throw IllegalArgumentException("Received invalid permission request type")
            }
            respFuture.complete(PermissionManagementResponse(response))
        } catch (e: Exception) {
            log.warn(e.message)
            respFuture.completeExceptionally(e)
        }
    }

    private fun createUser(request: CreateUserRequest, requestUserId: String): AvroUser {
        val loginName = request.loginName

        log.debug { "Received request to create new user: $loginName" }

        return entityManagerFactory.commit { entityManager ->

            requireNewUser(entityManager, loginName)

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
            user.version = 0

            entityManager.persist(user)

            val auditLog = ChangeAudit(
                id = UUID.randomUUID().toString(),
                updateTimestamp = user.updateTimestamp,
                actorUser = requestUserId,
                changeType = RPCPermissionOperation.USER_INSERT,
                details = "User '${user.loginName}' created by '$requestUserId'."
            )

            entityManager.persist(auditLog)

            user.toAvroUser()
        }.also {
            log.info("Successfully created new user: $loginName")
        }
    }

    private fun requireNewUser(entityManager: EntityManager, loginName: String) {
        val result = entityManager
            .createQuery("SELECT count(1) FROM User WHERE loginName = :loginName")
            .setParameter("loginName", loginName)
            .singleResult as Long

        require(result == 0L) { "Failed to create new user: $loginName as they already exist" }
    }

    private fun User.toAvroUser(): AvroUser {
        return AvroUser(
            id,
            version,
            ChangeDetails(updateTimestamp),
            loginName,
            fullName,
            enabled,
            hashedPassword,
            saltValue,
            passwordExpiry,
            false,
            parentGroup?.id,
            emptyList(),
            emptyList()
        )
    }
}