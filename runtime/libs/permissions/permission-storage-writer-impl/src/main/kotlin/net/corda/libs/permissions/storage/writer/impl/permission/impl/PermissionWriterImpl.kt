package net.corda.libs.permissions.storage.writer.impl.permission.impl

import net.corda.data.permissions.management.permission.CreatePermissionRequest
import net.corda.libs.permissions.storage.common.converter.toAvroPermission
import net.corda.libs.permissions.storage.common.converter.toDbModelPermissionType
import net.corda.libs.permissions.storage.writer.impl.permission.PermissionWriter
import net.corda.libs.permissions.storage.writer.impl.validation.EntityValidationUtil
import net.corda.orm.utils.transaction
import net.corda.permissions.model.ChangeAudit
import net.corda.permissions.model.Permission
import net.corda.permissions.model.RestPermissionOperation
import net.corda.utilities.debug
import org.slf4j.LoggerFactory
import java.time.Instant
import java.util.UUID
import javax.persistence.EntityManagerFactory
import net.corda.data.permissions.Permission as AvroPermission

class PermissionWriterImpl(
    private val entityManagerFactory: EntityManagerFactory
) : PermissionWriter {

    private companion object {
        val log = LoggerFactory.getLogger(this::class.java.enclosingClass)
    }

    override fun createPermission(request: CreatePermissionRequest, requestUserId: String, virtualNodeId: String?): AvroPermission {
        val permissionName = request.permissionType.name + " : " + request.permissionString

        log.debug { "Received request to create new permission: $permissionName." }

        return entityManagerFactory.transaction { entityManager ->

            val validator = EntityValidationUtil(entityManager)
            val groupVisibility = validator.validateAndGetOptionalParentGroup(request.groupVisibility)

            val updateTimestamp = Instant.now()
            val permission = Permission(
                id = UUID.randomUUID().toString(),
                updateTimestamp = updateTimestamp,
                groupVisibility = groupVisibility,
                virtualNodeId,
                request.permissionType.toDbModelPermissionType(),
                request.permissionString
            )

            entityManager.persist(permission)

            val auditLog = ChangeAudit(
                id = UUID.randomUUID().toString(),
                updateTimestamp = updateTimestamp,
                actorUser = requestUserId,
                changeType = RestPermissionOperation.PERMISSION_INSERT,
                details = "Permission '${permission.id}' with name '$permissionName' created by '$requestUserId'."
            )

            entityManager.persist(auditLog)

            log.info("Successfully created new permission: ${permission.id}.")

            permission.toAvroPermission()
        }
    }
}
