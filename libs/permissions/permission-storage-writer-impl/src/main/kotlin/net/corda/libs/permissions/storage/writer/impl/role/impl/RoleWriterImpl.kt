package net.corda.libs.permissions.storage.writer.impl.role.impl

import net.corda.data.permissions.management.role.AddPermissionToRoleRequest
import net.corda.data.permissions.management.role.CreateRoleRequest
import net.corda.data.permissions.management.role.RemovePermissionFromRoleRequest
import net.corda.libs.permissions.storage.common.converter.toAvroRole
import net.corda.libs.permissions.storage.writer.impl.role.RoleWriter
import net.corda.libs.permissions.storage.writer.impl.validation.EntityValidationUtil
import net.corda.orm.utils.transaction
import net.corda.permissions.model.ChangeAudit
import net.corda.permissions.model.Permission
import net.corda.permissions.model.RestPermissionOperation
import net.corda.permissions.model.Role
import net.corda.permissions.model.RolePermissionAssociation
import net.corda.utilities.debug
import org.slf4j.LoggerFactory
import java.time.Instant
import java.util.UUID
import javax.persistence.EntityManagerFactory
import net.corda.data.permissions.Role as AvroRole

class RoleWriterImpl(
    private val entityManagerFactory: EntityManagerFactory
) : RoleWriter {

    private companion object {
        val log = LoggerFactory.getLogger(this::class.java.enclosingClass)
    }

    override fun createRole(request: CreateRoleRequest, requestUserId: String): AvroRole {
        val roleName = request.roleName

        log.debug { "Received request to create new role: $roleName." }

        return entityManagerFactory.transaction { entityManager ->

            val validator = EntityValidationUtil(entityManager)
            val groupVisibility = validator.validateAndGetOptionalParentGroup(request.groupVisibility)

            val updateTimestamp = Instant.now()
            val role = Role(
                id = UUID.randomUUID().toString(),
                updateTimestamp = updateTimestamp,
                name = request.roleName,
                groupVisibility = groupVisibility
            )

            entityManager.persist(role)

            val auditLog = ChangeAudit(
                id = UUID.randomUUID().toString(),
                updateTimestamp = updateTimestamp,
                actorUser = requestUserId,
                changeType = RestPermissionOperation.ROLE_INSERT,
                details = "Role '${role.id}' with name '$roleName' created by '$requestUserId'."
            )

            entityManager.persist(auditLog)

            log.info("Successfully created new role: ${role.id} ($roleName).")

            role.toAvroRole()
        }
    }

    override fun addPermissionToRole(request: AddPermissionToRoleRequest, requestUserId: String): AvroRole {

        log.debug { "Received request to add permission to a role: $request" }

        return entityManagerFactory.transaction { entityManager ->

            val validator = EntityValidationUtil(entityManager)
            val role = validator.requireEntityExists(Role::class.java, request.roleId)
            validator.requirePermissionNotAssociatedWithRole(role.rolePermAssociations, request.permissionId, request.roleId)
            val permission = validator.requireEntityExists(Permission::class.java, request.permissionId)

            val updateTimestamp = Instant.now()

            val rolePermissionAssociation =
                RolePermissionAssociation(UUID.randomUUID().toString(), role, permission, updateTimestamp)
            role.rolePermAssociations.add(rolePermissionAssociation)

            // Note: We are not making checks whether a permission is already associated with a role.
            // Should this be the case, there is a unique constraint (`rest_role_perm_rel_uc1`) which will not let
            // this happen.
            entityManager.merge(role)

            val auditLog = ChangeAudit(
                id = UUID.randomUUID().toString(),
                updateTimestamp = updateTimestamp,
                actorUser = requestUserId,
                changeType = RestPermissionOperation.ADD_PERMISSION_TO_ROLE,
                details = "Role '${role.id}' got permission assigned '${permission.id}' by '$requestUserId'."
            )

            entityManager.persist(auditLog)

            log.info("Successfully added permission to a role: $request.")

            role.toAvroRole()
        }
    }

    override fun removePermissionFromRole(request: RemovePermissionFromRoleRequest, requestUserId: String): AvroRole {
        log.debug { "Received request to remove permission from a role: $request" }

        return entityManagerFactory.transaction { entityManager ->

            val validator = EntityValidationUtil(entityManager)
            val role = validator.requireEntityExists(Role::class.java, request.roleId)
            val rolePermissionAssociation =
                validator.requireRoleAssociatedWithPermission(role.rolePermAssociations, request.permissionId, request.roleId)

            val updateTimestamp = Instant.now()

            role.rolePermAssociations.remove(rolePermissionAssociation)

            entityManager.merge(role)
            entityManager.remove(rolePermissionAssociation)

            val auditLog = ChangeAudit(
                id = UUID.randomUUID().toString(),
                updateTimestamp = updateTimestamp,
                actorUser = requestUserId,
                changeType = RestPermissionOperation.DELETE_PERMISSION_FROM_ROLE,
                details = "Role '${role.id}' got permission removed '${request.permissionId}' by '$requestUserId'."
            )

            entityManager.persist(auditLog)

            log.info("Successfully removed permission from a role: $request.")

            role.toAvroRole()
        }
    }
}