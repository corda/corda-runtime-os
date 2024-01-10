package net.corda.libs.permissions.storage.reader.impl

import net.corda.libs.permissions.management.cache.PermissionManagementCache
import net.corda.libs.permissions.storage.common.converter.toAvroGroup
import net.corda.libs.permissions.storage.common.converter.toAvroPermission
import net.corda.libs.permissions.storage.common.converter.toAvroRole
import net.corda.libs.permissions.storage.common.converter.toAvroUser
import net.corda.libs.permissions.storage.reader.PermissionStorageReader
import net.corda.libs.permissions.storage.reader.repository.PermissionRepository
import net.corda.libs.permissions.storage.reader.repository.UserLogin
import net.corda.libs.permissions.storage.reader.summary.InternalUserPermissionSummary
import net.corda.libs.permissions.storage.reader.summary.PermissionSummaryReconciler
import net.corda.libs.permissions.validation.cache.PermissionValidationCache
import net.corda.messaging.api.publisher.Publisher
import net.corda.messaging.api.records.Record
import net.corda.permissions.model.Group
import net.corda.permissions.model.Permission
import net.corda.permissions.model.Role
import net.corda.permissions.model.User
import net.corda.schema.Schemas.Permissions.PERMISSIONS_USER_SUMMARY_TOPIC
import net.corda.schema.Schemas.Rest.REST_PERM_ENTITY_TOPIC
import net.corda.schema.Schemas.Rest.REST_PERM_GROUP_TOPIC
import net.corda.schema.Schemas.Rest.REST_PERM_ROLE_TOPIC
import net.corda.schema.Schemas.Rest.REST_PERM_USER_TOPIC
import net.corda.utilities.concurrent.getOrThrow
import net.corda.utilities.trace
import org.slf4j.LoggerFactory
import java.util.concurrent.atomic.AtomicReference
import net.corda.data.permissions.Group as AvroGroup
import net.corda.data.permissions.Permission as AvroPermission
import net.corda.data.permissions.Role as AvroRole
import net.corda.data.permissions.User as AvroUser
import net.corda.data.permissions.summary.UserPermissionSummary as AvroUserPermissionSummary

@Suppress("TooManyFunctions")
class PermissionStorageReaderImpl(
    private val permissionValidationCacheRef: AtomicReference<PermissionValidationCache?>,
    private val permissionManagementCacheRef: AtomicReference<PermissionManagementCache?>,
    private val permissionRepository: PermissionRepository,
    private val publisher: Publisher,
    private val permissionSummaryReconciler: PermissionSummaryReconciler,
) : PermissionStorageReader {

    private companion object {
        val log = LoggerFactory.getLogger(this::class.java.enclosingClass)
    }

    private var stopped = false

    private val permissionManagementCache: PermissionManagementCache
        get() = checkNotNull(permissionManagementCacheRef.get()) {
            "Permission management cache is null."
        }

    override fun start() {
        stopped = false
        publishOnStartup()
    }

    override fun close() {
        stopped = true
    }

    override fun publishNewUser(user: AvroUser) {
        publishUpdatedUser(user)
    }

    override fun publishNewRole(role: AvroRole) {
        publishUpdatedRole(role)
    }

    override fun publishUpdatedUser(user: AvroUser) {
        publisher.publish(listOf(Record(REST_PERM_USER_TOPIC, key = user.loginName, value = user))).single().getOrThrow()
    }

    override fun publishUpdatedRole(role: AvroRole) {
        publisher.publish(listOf(Record(REST_PERM_ROLE_TOPIC, key = role.id, value = role))).single().getOrThrow()
    }

    override fun publishNewPermission(permission: AvroPermission) {
        publisher.publish(listOf(Record(REST_PERM_ENTITY_TOPIC, key = permission.id, value = permission))).single().getOrThrow()
    }

    override fun publishGroups(ids: List<String>) {
        publisher.publish(createGroupRecords(permissionRepository.findAllGroups(ids)))
    }

    override fun publishRoles(ids: List<String>) {
        publisher.publish(createRoleRecords(permissionRepository.findAllRoles(ids)))
    }

    override fun reconcilePermissionSummaries() {
        log.trace { "Reconciliation of permission summaries triggered." }
        val startTime = System.currentTimeMillis()

        val permissionSummariesFromDb: Map<UserLogin, InternalUserPermissionSummary> = permissionRepository.findAllPermissionSummaries()

        val permissionValidationCache = checkNotNull(permissionValidationCacheRef.get()) {
            "Permission validation cache is null."
        }

        val permissionsToReconcile: Map<UserLogin, AvroUserPermissionSummary?> = permissionSummaryReconciler.getSummariesForReconciliation(
            permissionSummariesFromDb,
            permissionValidationCache.permissionSummaries
        )

        if (permissionsToReconcile.isNotEmpty()) {
            publisher.publish(createPermissionSummaryRecords(permissionsToReconcile))
            val duration = System.currentTimeMillis() - startTime
            log.info("Permission summary reconciliation completed and published ${permissionsToReconcile.size} user(s) in ${duration}ms.")
        } else {
            log.trace { "Permission summary reconciliation found everything up-to-date." }
        }
    }

    private fun publishOnStartup() {
        log.info("Publishing on start-up")
        publisher.publish(createUserRecords(permissionRepository.findAllUsers()))
        publisher.publish(createGroupRecords(permissionRepository.findAllGroups()))
        publisher.publish(createRoleRecords(permissionRepository.findAllRoles()))
        publisher.publish(createPermissionRecords(permissionRepository.findAllPermissions()))
        reconcilePermissionSummaries()
    }

    private fun createUserRecords(users: List<User>): List<Record<String, AvroUser>> {
        val userNames = users.map { it.loginName }.toHashSet()
        val updated = users.map { user ->
            Record(REST_PERM_USER_TOPIC, key = user.loginName, value = user.toAvroUser())
        }

        val removed: List<Record<String, AvroUser>> = permissionManagementCache.users
            .filterKeys { loginName -> loginName !in userNames }
            .map { (loginName, _) -> Record(REST_PERM_USER_TOPIC, key = loginName, value = null) }

        return updated + removed
    }

    private fun createGroupRecords(groups: List<Group>): List<Record<String, AvroGroup>> {
        val groupIds = groups.map { it.id }.toHashSet()
        val updated = groups.map { group ->
            Record(REST_PERM_GROUP_TOPIC, key = group.id, value = group.toAvroGroup())
        }
        val removed: List<Record<String, AvroGroup>> = permissionManagementCache.groups
            .filterKeys { id -> id !in groupIds }
            .map { (id, _) -> Record(REST_PERM_GROUP_TOPIC, key = id, value = null) }

        return updated + removed
    }

    private fun createRoleRecords(roles: List<Role>): List<Record<String, AvroRole>> {
        val roleIds = roles.map { it.id }.toHashSet()
        val updated = roles.map { role ->
            Record(REST_PERM_ROLE_TOPIC, key = role.id, value = role.toAvroRole())
        }
        val removed: List<Record<String, AvroRole>> = permissionManagementCache.roles
            .filterKeys { id -> id !in roleIds }
            .map { (id, _) -> Record(REST_PERM_ROLE_TOPIC, key = id, value = null) }

        return updated + removed
    }

    private fun createPermissionRecords(permissions: List<Permission>): List<Record<String, AvroPermission>> {
        val permissionIds = permissions.map { it.id }.toHashSet()
        val updated = permissions.map { perm ->
            Record(REST_PERM_ENTITY_TOPIC, key = perm.id, value = perm.toAvroPermission())
        }
        val removed: List<Record<String, AvroPermission>> = permissionManagementCache.permissions
            .filterKeys { id -> id !in permissionIds }
            .map { (id, _) -> Record(REST_PERM_ENTITY_TOPIC, key = id, value = null) }

        return updated + removed
    }

    private fun createPermissionSummaryRecords(summaries: Map<UserLogin, AvroUserPermissionSummary?>):
            List<Record<String, AvroUserPermissionSummary>> {

        return summaries.map {
            // summaries with null value are removal records for the user
            Record(PERMISSIONS_USER_SUMMARY_TOPIC, it.key, it.value)
        }
    }
}
