package net.corda.libs.permissions.storage.writer.impl.permission.impl

import java.time.Instant
import java.util.UUID
import javax.persistence.EntityManagerFactory
import net.corda.data.permissions.management.permission.CreatePermissionRequest
import net.corda.libs.permissions.storage.common.converter.fromAvroPermissionType
import net.corda.libs.permissions.storage.common.converter.toAvroPermission
import net.corda.libs.permissions.storage.writer.impl.permission.PermissionWriter
import net.corda.orm.utils.transaction
import net.corda.permissions.model.ChangeAudit
import net.corda.permissions.model.Group
import net.corda.permissions.model.RPCPermissionOperation
import net.corda.permissions.model.Permission
import net.corda.v5.base.util.contextLogger
import net.corda.v5.base.util.debug
import net.corda.data.permissions.Permission as AvroPermission

class PermissionWriterImpl(
    private val entityManagerFactory: EntityManagerFactory
) : PermissionWriter {

    private companion object {
        val log = contextLogger()
    }

    override fun createPermission(request: CreatePermissionRequest, requestUserId: String, virtualNodeId: String?): AvroPermission {
        val permissionName = request.permissionType.name + " : " + request.permissionString

        log.debug { "Received request to create new permission: $permissionName." }

        return entityManagerFactory.transaction { entityManager ->
            val groupVisibility = if (request.groupVisibility != null) {
                requireNotNull(entityManager.find(Group::class.java, request.groupVisibility)) {
                    "Failed to create new Permission: $permissionName as the specified group visibility: ${request.groupVisibility} does not exist."
                }
            } else {
                null
            }

            val updateTimestamp = Instant.now()
            val permission = Permission(
                id = UUID.randomUUID().toString(),
                updateTimestamp = updateTimestamp,
                groupVisibility = groupVisibility,
                virtualNodeId,
                request.permissionType.fromAvroPermissionType(),
                request.permissionString
            )
            permission.version = 0

            entityManager.persist(permission)

            val auditLog = ChangeAudit(
                id = UUID.randomUUID().toString(),
                updateTimestamp = updateTimestamp,
                actorUser = requestUserId,
                changeType = RPCPermissionOperation.PERMISSION_INSERT,
                details = "Permission '$permissionName' created by '$requestUserId'."
            )

            entityManager.persist(auditLog)

            log.info("Successfully created new permission: $permissionName.")

            permission.toAvroPermission()
        }
    }
}
