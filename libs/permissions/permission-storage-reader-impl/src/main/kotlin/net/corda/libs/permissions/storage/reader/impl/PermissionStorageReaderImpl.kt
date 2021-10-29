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
import javax.persistence.EntityManager
import javax.persistence.EntityManagerFactory

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

    override fun publish(userIds: List<String>, groupIds: List<String>, rolesIds: List<String>) {
        publish(findAll(userIds, groupIds, rolesIds))
    }

    private fun publishOnStartup() {
        publish(findAll())
    }

    private fun publish(entities: Triple<List<User>, List<Group>, List<Role>>) {
        val (users, groups, roles) = entities

        val userRecords = createUserRecords(users)
        val groupRecords = createGroupRecords(groups)
        val roleRecords = createRoleRecords(roles)

        if (userRecords.isNotEmpty() || groupRecords.isNotEmpty() || roleRecords.isNotEmpty()) {
            publisher.publish(userRecords + groupRecords + roleRecords)
        }
    }

    private fun createUserRecords(users: List<User>): List<Record<String, net.corda.data.permissions.User>> {
        val userNames = users.map { it.loginName }.toHashSet()
        val updated = users.map { user ->
            Record(RPC_PERM_USER_TOPIC, key = user.loginName, value = user.toAvroUser())
        }
        val removed: List<Record<String, net.corda.data.permissions.User>> = permissionCache.users
            .filterKeys { loginName -> loginName !in userNames }
            .map { (loginName, _) -> Record(RPC_PERM_USER_TOPIC, key = loginName, value = null) }

        return updated + removed
    }

    private fun createGroupRecords(groups: List<Group>): List<Record<String, net.corda.data.permissions.Group>> {
        val groupNames = groups.map { it.name }.toHashSet()
        val updated = groups.map { group ->
            Record(RPC_PERM_GROUP_TOPIC, key = group.name, value = group.toAvroGroup())
        }
        val removed: List<Record<String, net.corda.data.permissions.Group>> = permissionCache.groups
            .filterKeys { name -> name !in groupNames }
            .map { (name, _) -> Record(RPC_PERM_GROUP_TOPIC, key = name, value = null) }

        return updated + removed
    }

    private fun createRoleRecords(roles: List<Role>): List<Record<String, net.corda.data.permissions.Role>> {
        val roleNames = roles.map { it.name }.toHashSet()
        val updated = roles.map { role ->
            Record(RPC_PERM_ROLE_TOPIC, key = role.name, value = role.toAvroRole())
        }
        val removed: List<Record<String, net.corda.data.permissions.Role>> = permissionCache.roles
            .filterKeys { name -> name !in roleNames }
            .map { (name, _) -> Record(RPC_PERM_ROLE_TOPIC, key = name, value = null) }

        return updated + removed
    }

    private fun findAll(): Triple<List<User>, List<Group>, List<Role>> {
        val entityManager = entityManagerFactory.createEntityManager()
        return try {
            entityManager.transaction.begin()
            val users = entityManager.createQuery("SELECT u from User u", User::class.java).resultList
            val groups = entityManager.createQuery("SELECT g from Group g", Group::class.java).resultList
            val roles = entityManager.createQuery("SELECT r from Role r", Role::class.java).resultList
            Triple(users, groups, roles)
        } finally {
            entityManager.close()
        }
    }

    private fun findAll(
        userIds: List<String>,
        groupIds: List<String>,
        rolesIds: List<String>
    ): Triple<List<User>, List<Group>, List<Role>> {
        val entityManager = entityManagerFactory.createEntityManager()
        return try {
            entityManager.transaction.begin()
            val users = entityManager.chunkedInQuery<User>("SELECT u from User u WHERE u.id IN :ids", userIds)
            val groups = entityManager.chunkedInQuery<Group>("SELECT g from Group g WHERE g.id IN :ids", groupIds)
            val roles = entityManager.chunkedInQuery<Role>("SELECT r from Role r WHERE r.id IN :ids", rolesIds)
            Triple(users, groups, roles)
        } finally {
            entityManager.close()
        }
    }

    private inline fun <reified T> EntityManager.chunkedInQuery(qlString: String, ids: List<String>): List<T> {
        return ids.chunked(100) { chunkedIds ->
            createQuery(qlString, T::class.java)
                .setParameter("ids", chunkedIds)
                .resultList
        }.flatten()
    }
}