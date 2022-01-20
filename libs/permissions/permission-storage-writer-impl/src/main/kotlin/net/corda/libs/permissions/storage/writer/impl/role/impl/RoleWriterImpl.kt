package net.corda.libs.permissions.storage.writer.impl.role.impl

import net.corda.data.permissions.management.role.AddPermissionToRoleRequest
import java.time.Instant
import java.util.UUID
import javax.persistence.EntityManagerFactory
import net.corda.data.permissions.management.role.CreateRoleRequest
import net.corda.data.permissions.management.role.RemovePermissionFromRoleRequest
import net.corda.libs.permissions.storage.common.converter.toAvroRole
import net.corda.libs.permissions.storage.writer.impl.role.RoleWriter
import net.corda.orm.utils.transaction
import net.corda.permissions.model.ChangeAudit
import net.corda.permissions.model.Group
import net.corda.permissions.model.Permission
import net.corda.permissions.model.RPCPermissionOperation
import net.corda.permissions.model.Role
import net.corda.permissions.model.RolePermissionAssociation
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

    override fun addPermissionToRole(request: AddPermissionToRoleRequest, requestUserId: String): AvroRole {

        log.debug { "Received request to add permission to a role: $request" }

        return entityManagerFactory.transaction { entityManager ->

            val role = requireNotNull(entityManager.find(Role::class.java, request.roleId)) {
                "Unable to find Role with Id: ${request.roleId}"
            }

            require(role.rolePermAssociations.none { it.permission.id == request.permissionId }) {
                "Permission '${request.permissionId}' is already associated with Role '${request.roleId}'."
            }

            val permission = requireNotNull(entityManager.find(Permission::class.java, request.permissionId)) {
                "Unable to find Permission with Id: ${request.permissionId}"
            }

            val updateTimestamp = Instant.now()

            val rolePermissionAssociation =
                RolePermissionAssociation(UUID.randomUUID().toString(), role, permission, updateTimestamp)
            role.rolePermAssociations.add(rolePermissionAssociation)

            // Note: We are not making checks whether a permission is already associated with a role.
            // Should this be the case, there is a unique constraint (`rpc_role_perm_rel_uc1`) which will not let
            // this happen.
            entityManager.merge(role)

            val auditLog = ChangeAudit(
                id = UUID.randomUUID().toString(),
                updateTimestamp = updateTimestamp,
                actorUser = requestUserId,
                changeType = RPCPermissionOperation.ADD_PERMISSION_TO_ROLE,
                details = "Permission '${permission.id}' assigned to Role '${role.id}' by '$requestUserId'. " +
                        "Created RolePermissionAssociation '${rolePermissionAssociation.id}'."
            )

            entityManager.persist(auditLog)

            log.info("Successfully added permission to a role: $request.")

            role.toAvroRole()
        }
    }

    override fun removePermissionFromRole(request: RemovePermissionFromRoleRequest, requestUserId: String): AvroRole {
        log.debug { "Received request to remove permission from a role: $request" }

        return entityManagerFactory.transaction { entityManager ->

            val role = requireNotNull(entityManager.find(Role::class.java, request.roleId)) {
                "Unable to find Role with Id: ${request.roleId}"
            }

            val rolePermissionAssociation =
                requireNotNull(role.rolePermAssociations.find { it.permission.id == request.permissionId }) {
                    "Permission with Id: ${request.permissionId} is not associated with a role: ${role.id}."
                }

            val updateTimestamp = Instant.now()

            role.rolePermAssociations.remove(rolePermissionAssociation)

            entityManager.merge(role)
            entityManager.remove(rolePermissionAssociation)

            val auditLog = ChangeAudit(
                id = UUID.randomUUID().toString(),
                updateTimestamp = updateTimestamp,
                actorUser = requestUserId,
                changeType = RPCPermissionOperation.DELETE_PERMISSION_FROM_ROLE,
                details = "Permission '${request.permissionId} removed from Role '${role.id}' by '$requestUserId'. " +
                        "Removed RolePermissionAssociation '${rolePermissionAssociation.id}'."
            )

            entityManager.persist(auditLog)

            log.info("Successfully removed permission from a role: $request.")

            role.toAvroRole()
        }
    }
}