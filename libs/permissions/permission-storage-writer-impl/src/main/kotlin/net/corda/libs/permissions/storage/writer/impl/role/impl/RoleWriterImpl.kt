package net.corda.libs.permissions.storage.writer.impl.role.impl

import java.time.Instant
import java.util.UUID
import javax.persistence.EntityManager
import javax.persistence.EntityManagerFactory
import net.corda.data.permissions.management.role.CreateRoleRequest
import net.corda.libs.permissions.storage.writer.impl.common.toAvroRole
import net.corda.libs.permissions.storage.writer.impl.role.RoleWriter
import net.corda.permissions.model.Group
import net.corda.permissions.model.Role
import net.corda.v5.base.util.contextLogger
import net.corda.v5.base.util.debug

class RoleWriterImpl(
    private val entityManagerFactory: EntityManagerFactory
) : RoleWriter {

    private companion object {
        val log = contextLogger()
    }

    override fun createRole(request: CreateRoleRequest): net.corda.data.permissions.Role {
        val roleName = request.roleName

        log.debug { "Received request to create new role: $roleName." }

        val entityManager = entityManagerFactory.createEntityManager()

        return try {
            entityManager.transaction.begin()

            requireRoleNotExists(entityManager, roleName)

            val groupVisibility = if (request.groupVisibility != null) {
                requireNotNull(entityManager.find(Group::class.java, request.groupVisibility)) {
                    "Failed to create new Role: $roleName as the specified group visibility: ${request.groupVisibility} does not exist."
                }
            } else {
                null
            }

            val role = Role(
                id = UUID.randomUUID().toString(),
                updateTimestamp = Instant.now(),
                name = request.roleName,
                groupVisibility = groupVisibility
            )
            role.version = 0

            entityManager.persist(role)
            entityManager.transaction.commit()

            log.info("Successfully created new role: $roleName.")

            role.toAvroRole()
        } finally {
            entityManager.close()
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