package net.corda.libs.permissions.storage.reader.impl

import net.corda.libs.permissions.cache.PermissionCache
import net.corda.libs.permissions.storage.reader.PermissionStorageReader
import net.corda.libs.permissions.storage.reader.repository.PermissionRepository
import net.corda.libs.permissions.storage.reader.toAvroGroup
import net.corda.libs.permissions.storage.reader.toAvroRole
import net.corda.libs.permissions.storage.reader.toAvroUser
import net.corda.messaging.api.publisher.Publisher
import net.corda.messaging.api.records.Record
import net.corda.permissions.model.Group
import net.corda.permissions.model.Role
import net.corda.permissions.model.User
import net.corda.rpc.schema.Schema.Companion.RPC_PERM_GROUP_TOPIC
import net.corda.rpc.schema.Schema.Companion.RPC_PERM_ROLE_TOPIC
import net.corda.rpc.schema.Schema.Companion.RPC_PERM_USER_TOPIC
import net.corda.v5.base.concurrent.getOrThrow
import net.corda.v5.base.util.contextLogger
import net.corda.data.permissions.Group as AvroGroup
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
        publisher.publish(listOf(Record(RPC_PERM_USER_TOPIC, key = user.loginName, value = user))).single().getOrThrow()
    }

    override fun publishNewRole(role: AvroRole) {
        publisher.publish(listOf(Record(RPC_PERM_ROLE_TOPIC, key = role.id, value = role))).single().getOrThrow()
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
        val groupNames = groups.map { it.name }.toHashSet()
        val updated = groups.map { group ->
            Record(RPC_PERM_GROUP_TOPIC, key = group.name, value = group.toAvroGroup())
        }
        val removed: List<Record<String, AvroGroup>> = permissionCache.groups
            .filterKeys { name -> name !in groupNames }
            .map { (name, _) -> Record(RPC_PERM_GROUP_TOPIC, key = name, value = null) }

        return updated + removed
    }

    private fun createRoleRecords(roles: List<Role>): List<Record<String, AvroRole>> {
        val roleNames = roles.map { it.name }.toHashSet()
        val updated = roles.map { role ->
            Record(RPC_PERM_ROLE_TOPIC, key = role.name, value = role.toAvroRole())
        }
        val removed: List<Record<String, AvroRole>> = permissionCache.roles
            .filterKeys { name -> name !in roleNames }
            .map { (name, _) -> Record(RPC_PERM_ROLE_TOPIC, key = name, value = null) }

        return updated + removed
    }
}