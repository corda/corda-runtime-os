package net.corda.libs.permissions.storage.reader.impl

import net.corda.libs.permissions.cache.PermissionCache
import net.corda.libs.permissions.storage.common.converter.toAvroGroup
import net.corda.libs.permissions.storage.common.converter.toAvroPermission
import net.corda.libs.permissions.storage.common.converter.toAvroRole
import net.corda.libs.permissions.storage.common.converter.toAvroUser
import net.corda.libs.permissions.storage.reader.PermissionStorageReader
import net.corda.libs.permissions.storage.reader.repository.PermissionRepository
import net.corda.messaging.api.publisher.Publisher
import net.corda.messaging.api.records.Record
import net.corda.permissions.model.Group
import net.corda.permissions.model.Permission
import net.corda.permissions.model.Role
import net.corda.permissions.model.User
import net.corda.schema.Schemas.RPC.Companion.RPC_PERM_ENTITY_TOPIC
import net.corda.schema.Schemas.RPC.Companion.RPC_PERM_GROUP_TOPIC
import net.corda.schema.Schemas.RPC.Companion.RPC_PERM_ROLE_TOPIC
import net.corda.schema.Schemas.RPC.Companion.RPC_PERM_USER_TOPIC
import net.corda.v5.base.concurrent.getOrThrow
import net.corda.v5.base.util.contextLogger
import net.corda.data.permissions.Group as AvroGroup
import net.corda.data.permissions.Permission as AvroPermission
import net.corda.data.permissions.Role as AvroRole
import net.corda.data.permissions.User as AvroUser

class PermissionStorageReaderImpl(
    private val permissionCache: PermissionCache,
    private val permissionRepository: PermissionRepository,
    private val publisher: Publisher,
) : PermissionStorageReader {

    private companion object {
        val log = contextLogger()
    }

    override val isRunning: Boolean get() = !stopped

    private var stopped = false

    override fun start() {
        stopped = false
        publishOnStartup()
    }

    override fun stop() {
        stopped = true
    }

    override fun publishNewUser(user: AvroUser) {
        publishUpdatedUser(user)
    }

    override fun publishNewRole(role: AvroRole) {
        publishUpdatedRole(role)
    }

    override fun publishUpdatedUser(user: AvroUser) {
        publisher.publish(listOf(Record(RPC_PERM_USER_TOPIC, key = user.loginName, value = user))).single().getOrThrow()
    }

    override fun publishUpdatedRole(role: AvroRole) {
        publisher.publish(listOf(Record(RPC_PERM_ROLE_TOPIC, key = role.id, value = role))).single().getOrThrow()
    }

    override fun publishNewPermission(permission: AvroPermission) {
        publisher.publish(listOf(Record(RPC_PERM_ENTITY_TOPIC, key = permission.id, value = permission))).single().getOrThrow()
    }

    override fun publishGroups(ids: List<String>) {
        publisher.publish(createGroupRecords(permissionRepository.findAllGroups(ids)))
    }

    override fun publishRoles(ids: List<String>) {
        publisher.publish(createRoleRecords(permissionRepository.findAllRoles(ids)))
    }

    private fun publishOnStartup() {
        log.info("Publishing on start-up")
        publisher.publish(createUserRecords(permissionRepository.findAllUsers()))
        publisher.publish(createGroupRecords(permissionRepository.findAllGroups()))
        publisher.publish(createRoleRecords(permissionRepository.findAllRoles()))
        publisher.publish(createPermissionRecords(permissionRepository.findAllPermissions()))
    }

    private fun createUserRecords(users: List<User>): List<Record<String, AvroUser>> {
        val userNames = users.map { it.loginName }.toHashSet()
        val updated = users.map { user ->
            Record(RPC_PERM_USER_TOPIC, key = user.loginName, value = user.toAvroUser())
        }
        val removed: List<Record<String, AvroUser>> = permissionCache.users
            .filterKeys { loginName -> loginName !in userNames }
            .map { (loginName, _) -> Record(RPC_PERM_USER_TOPIC, key = loginName, value = null) }

        return updated + removed
    }

    private fun createGroupRecords(groups: List<Group>): List<Record<String, AvroGroup>> {
        val groupIds = groups.map { it.id }.toHashSet()
        val updated = groups.map { group ->
            Record(RPC_PERM_GROUP_TOPIC, key = group.id, value = group.toAvroGroup())
        }
        val removed: List<Record<String, AvroGroup>> = permissionCache.groups
            .filterKeys { id -> id !in groupIds }
            .map { (id, _) -> Record(RPC_PERM_GROUP_TOPIC, key = id, value = null) }

        return updated + removed
    }

    private fun createRoleRecords(roles: List<Role>): List<Record<String, AvroRole>> {
        val roleIds = roles.map { it.id }.toHashSet()
        val updated = roles.map { role ->
            Record(RPC_PERM_ROLE_TOPIC, key = role.id, value = role.toAvroRole())
        }
        val removed: List<Record<String, AvroRole>> = permissionCache.roles
            .filterKeys { id -> id !in roleIds }
            .map { (id, _) -> Record(RPC_PERM_ROLE_TOPIC, key = id, value = null) }

        return updated + removed
    }

    private fun createPermissionRecords(permissions: List<Permission>): List<Record<String, AvroPermission>> {
        val permissionIds = permissions.map { it.id }.toHashSet()
        val updated = permissions.map { perm ->
            Record(RPC_PERM_ENTITY_TOPIC, key = perm.id, value = perm.toAvroPermission())
        }
        val removed: List<Record<String, AvroPermission>> = permissionCache.permissions
            .filterKeys { id -> id !in permissionIds }
            .map { (id, _) -> Record(RPC_PERM_ENTITY_TOPIC, key = id, value = null) }

        return updated + removed
    }
}