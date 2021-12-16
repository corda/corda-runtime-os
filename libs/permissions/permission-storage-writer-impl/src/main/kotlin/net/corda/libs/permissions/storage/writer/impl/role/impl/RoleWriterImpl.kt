package net.corda.libs.permissions.storage.writer.impl.role.impl

import java.time.Instant
import java.util.UUID
import javax.persistence.EntityManager
import javax.persistence.EntityManagerFactory
import net.corda.data.permissions.management.role.CreateRoleRequest
import net.corda.libs.permissions.storage.common.converter.toAvroRole
import net.corda.libs.permissions.storage.writer.impl.role.RoleWriter
import net.corda.orm.utils.transaction
import net.corda.permissions.model.ChangeAudit
import net.corda.permissions.model.Group
import net.corda.permissions.model.RPCPermissionOperation
import net.corda.permissions.model.Role
import net.corda.v5.base.util.contextLogger
import net.corda.v5.base.util.debug
import net.corda.data.permissions.Role as AvroRole

class RoleWriterImpl(
    private val entityManagerFactory: EntityManagerFactory
) : RoleWriter {

    private companion object {
        val log = contextLogger()
    }

    override fun createRole(request: CreateRoleRequest, requestUserId: String): AvroRole {
        val roleName = request.roleName

        log.debug { "Received request to create new role: $roleName." }

        return entityManagerFactory.transaction { entityManager ->
            requireRoleNotExists(entityManager, roleName)

            val groupVisibility = if (request.groupVisibility != null) {
                requireNotNull(entityManager.find(Group::class.java, request.groupVisibility)) {
                    "Failed to create new Role: $roleName as the specified group visibility: ${request.groupVisibility} does not exist."
                }
            } else {
                null
            }

            val updateTimestamp = Instant.now()
            val role = Role(
                id = UUID.randomUUID().toString(),
                updateTimestamp = updateTimestamp,
                name = request.roleName,
                groupVisibility = groupVisibility
            )
            role.version = 0

            entityManager.persist(role)

            val auditLog = ChangeAudit(
                id = UUID.randomUUID().toString(),
                updateTimestamp = updateTimestamp,
                actorUser = requestUserId,
                changeType = RPCPermissionOperation.ROLE_INSERT,
                details = "Role '${role.name}' created by '$requestUserId'."
            )

            entityManager.persist(auditLog)

            log.info("Successfully created new role: $roleName.")

            role.toAvroRole()
        }
    }

    private fun requireRoleNotExists(entityManager: EntityManager, roleName: String) {
        val result = entityManager
            .createQuery("SELECT count(1) FROM Role WHERE name = :roleName")
            .setParameter("roleName", roleName)
            .singleResult as Long

        require(result == 0L) { "Failed to create new role: $roleName as they already exist." }
    }
}