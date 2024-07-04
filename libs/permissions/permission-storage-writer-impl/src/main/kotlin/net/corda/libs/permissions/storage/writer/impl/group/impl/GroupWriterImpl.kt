package net.corda.libs.permissions.storage.writer.impl.group.impl

import net.corda.data.permissions.management.group.*
import net.corda.libs.permissions.storage.common.converter.toAvroGroup
import net.corda.libs.permissions.storage.writer.impl.group.GroupWriter
import net.corda.libs.permissions.storage.writer.impl.validation.EntityValidationUtil
import net.corda.orm.utils.transaction
import net.corda.permissions.model.ChangeAudit
import net.corda.permissions.model.Group
import net.corda.permissions.model.RestPermissionOperation
import net.corda.permissions.model.Role
import net.corda.permissions.model.RoleGroupAssociation
import net.corda.utilities.debug
import org.slf4j.LoggerFactory
import java.time.Instant
import java.util.UUID
import javax.persistence.EntityManager
import javax.persistence.EntityManagerFactory
import net.corda.data.permissions.Group as AvroGroup

class GroupWriterImpl(
    private val entityManagerFactory: EntityManagerFactory
) : GroupWriter {

    private companion object {
        val log = LoggerFactory.getLogger(this::class.java.enclosingClass)
    }

    override fun createGroup(request: CreateGroupRequest, requestUserId: String): AvroGroup {
        val groupName = request.groupName
        log.debug { "Received request to create new group: $groupName" }
        return entityManagerFactory.transaction { entityManager ->

            val validator = EntityValidationUtil(entityManager)
            val parentGroup = validator.validateAndGetOptionalParentGroup(request.parentGroupId)

            val group = persistNewGroup(request, parentGroup, entityManager, requestUserId, groupName)
            group.toAvroGroup()
        }
    }

    override fun changeParentGroup(
        request: ChangeGroupParentIdRequest,
        requestUserId: String
    ): AvroGroup {
        log.debug { "Received request to change parent group of Group ${request.groupId} to ${request.newParentGroupId}" }
        return entityManagerFactory.transaction { entityManager ->

            val validator = EntityValidationUtil(entityManager)
            val group = validator.validateAndGetUniqueGroup(request.groupId)
            val newParentGroup = validator.validateAndGetUniqueGroup(request.newParentGroupId)

            group.parentGroup = newParentGroup

            val updateTimestamp = Instant.now()
            val changeAudit = ChangeAudit(
                id = UUID.randomUUID().toString(),
                updateTimestamp = updateTimestamp,
                actorUser = requestUserId,
                changeType = RestPermissionOperation.GROUP_UPDATE,
                details = "Parent group of Group '${group.id}' changed to '${newParentGroup.id}' by '$requestUserId'."
            )

            entityManager.merge(group)
            entityManager.persist(changeAudit)

            group.toAvroGroup()
        }
    }

    override fun addRoleToGroup(
        request: AddRoleToGroupRequest,
        requestUserId: String
    ): AvroGroup {
        log.debug { "Received request to add Role ${request.roleId} to Group ${request.groupId}" }
        return entityManagerFactory.transaction { entityManager ->

            val validator = EntityValidationUtil(entityManager)
            val group = validator.validateAndGetUniqueGroup(request.groupId)
            val role = validator.validateAndGetUniqueRole(request.roleId)
            validator.validateRoleNotAlreadyAssignedToGroup(group, request.roleId)

            val resultGroup = persistGroupRoleAssociation(entityManager, requestUserId, group, role)

            resultGroup.toAvroGroup()
        }
    }

    override fun removeRoleFromGroup(
        request: RemoveRoleFromGroupRequest,
        requestUserId: String
    ): AvroGroup {
        log.debug { "Received request to remove Role ${request.roleId} from Group ${request.groupId}" }
        return entityManagerFactory.transaction { entityManager ->

            val validator = EntityValidationUtil(entityManager)
            val group = validator.validateAndGetUniqueGroup(request.groupId)
            val association = validator.validateAndGetRoleAssociatedWithGroup(group, request.roleId)

            val resultGroup = removeGroupRoleAssociation(entityManager, requestUserId, group, request.roleId, association)

            resultGroup.toAvroGroup()
        }
    }

    override fun deleteGroup(request: DeleteGroupRequest, requestUserId: String): AvroGroup {
        log.debug { "Received request to delete group: ${request.groupId}" }
        return entityManagerFactory.transaction { entityManager ->

            val validator = EntityValidationUtil(entityManager)
            val group = validator.validateAndGetUniqueGroup(request.groupId)
            validator.validateGroupIsEmpty(group)

            val resultGroup = removeGroup(entityManager, requestUserId, group)

            resultGroup.toAvroGroup()
        }
    }

    private fun persistNewGroup(
        request: CreateGroupRequest,
        parentGroup: Group?,
        entityManager: EntityManager,
        requestUserId: String,
        groupName: String
    ): Group {
        val updateTimestamp = Instant.now()
        val group = Group(
            id = UUID.randomUUID().toString(),
            updateTimestamp = updateTimestamp,
            name = request.groupName,
            parentGroup = parentGroup
        )

        entityManager.persist(group)

        val auditLog = ChangeAudit(
            id = UUID.randomUUID().toString(),
            updateTimestamp = updateTimestamp,
            actorUser = requestUserId,
            changeType = RestPermissionOperation.GROUP_INSERT,
            details = "Group '${group.name}' created by '$requestUserId'."
        )

        entityManager.persist(auditLog)

        log.info("Successfully created new group: $groupName.")
        return group
    }

    private fun persistGroupRoleAssociation(entityManager: EntityManager, requestUserId: String, group: Group, role: Role): Group {
        val updateTimestamp = Instant.now()
        val association = RoleGroupAssociation(UUID.randomUUID().toString(), role, group, updateTimestamp)
        val changeAudit = ChangeAudit(
            id = UUID.randomUUID().toString(),
            updateTimestamp = updateTimestamp,
            actorUser = requestUserId,
            changeType = RestPermissionOperation.ADD_ROLE_TO_GROUP,
            details = "Role '${role.id}' assigned to Group '${group.id}' by '$requestUserId'. " +
                "Created RoleGroupAssociation '${association.id}'."
        )

        group.roleGroupAssociations.add(association)

        // merging user cascades and adds the association
        entityManager.merge(group)
        entityManager.persist(changeAudit)

        return group
    }

    private fun removeGroupRoleAssociation(
        entityManager: EntityManager,
        requestUserId: String,
        group: Group,
        roleId: String,
        association: RoleGroupAssociation
    ): Group {
        val updateTimestamp = Instant.now()
        val changeAudit = ChangeAudit(
            id = UUID.randomUUID().toString(),
            updateTimestamp = updateTimestamp,
            actorUser = requestUserId,
            changeType = RestPermissionOperation.DELETE_ROLE_FROM_GROUP,
            details = "Role '$roleId' unassigned from Group '${group.id}' by '$requestUserId'. " +
                "Removed RoleGroupAssociation '${association.id}'."
        )

        group.roleGroupAssociations.remove(association)

        entityManager.remove(association)
        entityManager.merge(group)
        entityManager.persist(changeAudit)

        return group
    }

    private fun removeGroup(
        entityManager: EntityManager,
        requestUserId: String,
        group: Group
    ): Group {
        val updateTimestamp = Instant.now()
        val changeAudit = ChangeAudit(
            id = UUID.randomUUID().toString(),
            updateTimestamp = updateTimestamp,
            actorUser = requestUserId,
            changeType = RestPermissionOperation.GROUP_DELETE,
            details = "Group '${group.id}' deleted by '$requestUserId'."
        )

        entityManager.remove(group)
        entityManager.persist(changeAudit)

        return group
    }
}
