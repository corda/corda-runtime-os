package net.corda.libs.permissions.storage.reader.impl

import net.corda.libs.permissions.cache.PermissionCache
import net.corda.libs.permissions.storage.reader.PermissionStorageReader
import net.corda.messaging.api.publisher.Publisher
import net.corda.messaging.api.records.Record
import net.corda.permissions.model.Group
import net.corda.permissions.model.Role
import net.corda.permissions.model.User
import net.corda.rpc.schema.Schema.Companion.RPC_PERM_GROUP_TOPIC
import net.corda.rpc.schema.Schema.Companion.RPC_PERM_ROLE_TOPIC
import net.corda.rpc.schema.Schema.Companion.RPC_PERM_USER_TOPIC
import javax.persistence.EntityManagerFactory
import net.corda.data.permissions.Group as AvroGroup
import net.corda.data.permissions.Role as AvroRole
import net.corda.data.permissions.User as AvroUser

class PermissionStorageReaderImpl(
    private val permissionCache: PermissionCache,
    private val entityManagerFactory: EntityManagerFactory,
    private val publisher: Publisher
) : PermissionStorageReader {

    override val isRunning: Boolean get() = !stopped

    private var stopped = false

    override fun start() {
        stopped = false
        publishOnStartup()
    }

    override fun stop() {
        stopped = true
    }

    override fun publishUsers(ids: List<String>) {
        publisher.publish(createUserRecords(findAllUsers(ids)))
    }

    override fun publishGroups(ids: List<String>) {
        publisher.publish(createGroupRecords(findAllGroups(ids)))
    }

    override fun publishRoles(ids: List<String>) {
        publisher.publish(createRoleRecords(findAllRoles(ids)))
    }

    private fun publishOnStartup() {
        publisher.publish(createUserRecords(findAllUsers()))
        publisher.publish(createGroupRecords(findAllGroups()))
        publisher.publish(createRoleRecords(findAllRoles()))
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

    private fun findAllUsers(): List<User> {
        return findAll("SELECT u from User u")
    }

    private fun findAllGroups(): List<Group> {
        return findAll("SELECT g from Group g")
    }

    private fun findAllRoles(): List<Role> {
        return findAll("SELECT r from Role r")
    }

    private fun findAllUsers(ids: List<String>): List<User> {
        return findAll("SELECT u from User u WHERE u.id IN :ids", ids)
    }

    private fun findAllGroups(ids: List<String>): List<Group> {
        return findAll("SELECT g from Group g WHERE g.id IN :ids", ids)
    }

    private fun findAllRoles(ids: List<String>): List<Role> {
        return findAll("SELECT r from Role r WHERE r.id IN :ids", ids)
    }

    private inline fun <reified T> findAll(qlString: String): List<T> {
        val entityManager = entityManagerFactory.createEntityManager()
        return try {
            entityManager.transaction.begin()
            entityManager.createQuery(qlString, T::class.java).resultList
        } finally {
            entityManager.close()
        }
    }

    private inline fun <reified T> findAll(qlString: String, ids: List<String>): List<T> {
        val entityManager = entityManagerFactory.createEntityManager()
        return try {
            entityManager.transaction.begin()
            ids.chunked(100) { chunkedIds ->
                entityManager.createQuery(qlString, T::class.java)
                    .setParameter("ids", chunkedIds)
                    .resultList
            }.flatten()
        } finally {
            entityManager.close()
        }
    }
}