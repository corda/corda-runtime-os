package net.corda.libs.permission

import net.corda.data.permissions.Permission
import net.corda.data.permissions.PermissionType
import net.corda.messaging.api.subscription.CompactedSubscription
import net.corda.messaging.api.subscription.factory.SubscriptionFactory
import net.corda.messaging.api.subscription.factory.config.SubscriptionConfig
import net.corda.v5.base.util.contextLogger
import net.corda.v5.base.util.debug
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

class PermissionValidatorImpl(
    private val subscriptionFactory: SubscriptionFactory,
    private val userTopicProcessor: UserTopicProcessor,
    private val groupTopicProcessor: GroupTopicProcessor,
    private val roleTopicProcessor: RoleTopicProcessor
) : PermissionValidator {

    companion object {

        private val logger = contextLogger()

        // Those are to be removed in favor of constants from Corda-API
        private const val RPC_PERM_USER_TOPIC = "rpc.permissions.user"
        private const val RPC_PERM_GROUP_TOPIC = "rpc.permissions.group"
        private const val RPC_PERM_ROLE_TOPIC = "rpc.permissions.role"

        internal const val CONSUMER_GROUP = "PERMISSION_SERVICE"
    }

    @Volatile
    private var running = false

    private val lock = ReentrantLock()

    private var subscriptions: List<CompactedSubscription<String, *>>? = null


    override val isRunning: Boolean
        get() = running

    override fun start() {
        lock.withLock {
            if (subscriptions == null) {
                val userSubscription =
                    subscriptionFactory.createCompactedSubscription(
                        SubscriptionConfig(
                            CONSUMER_GROUP,
                            RPC_PERM_USER_TOPIC
                        ),
                        userTopicProcessor
                    ).also { it.start() }
                val groupSubscription =
                    subscriptionFactory.createCompactedSubscription(
                        SubscriptionConfig(
                            CONSUMER_GROUP,
                            RPC_PERM_GROUP_TOPIC
                        ),
                        groupTopicProcessor
                    ).also { it.start() }
                val roleSubscription =
                    subscriptionFactory.createCompactedSubscription(
                        SubscriptionConfig(
                            CONSUMER_GROUP,
                            RPC_PERM_ROLE_TOPIC
                        ),
                        roleTopicProcessor
                    ).also { it.start() }
                subscriptions = listOf(userSubscription, groupSubscription, roleSubscription)
                running = true
            }
        }
    }

    override fun stop() {
        lock.withLock {
            if (running) {
                subscriptions?.forEach { it.stop() }
                subscriptions = null
                running = false
            }
        }
    }

    override fun authorizeUser(requestId: String, loginName: String, permission: String): Boolean {

        logger.debug { "Checking permissions for $permission for user $loginName" }

        val user = userTopicProcessor.getUser(loginName) ?: return false

        if (!user.enabled) {
            logger.debug { "User $loginName is disabled" }
            return false
        }

        return performCheckRec(user.roleIds, user.parentGroupId, permission)
    }

    private tailrec fun performCheckRec(
        roleIds: Collection<String>,
        parentGroupId: String?,
        permission: String
    ): Boolean {

        logger.debug { "Checking permissions for: $permission - $roleIds - $parentGroupId" }

        if (roleIds.isEmpty() && parentGroupId == null) {
            logger.debug { "Roles are empty and no parent group left" }
            return false
        }

        // Should we report roles that cannot be found?
        val roles = roleIds.mapNotNull { roleTopicProcessor.getRole(it) }

        val permissionRequested = PermissionUrl.fromUrl(permission).permissionRequested
        val allPermissions = roles.flatMap { it.permissions }

        // Perform checks, with deny taking priority over allow
        val (denies, allows) = allPermissions.partition { it.type == PermissionType.DENY }
        if (denies.any { wildcardMatch(it, permissionRequested) }) {
            logger.debug { "Explicitly denied by: '${denies.first { wildcardMatch(it, permissionRequested) }}'" }
            return false
        }
        if (allows.any { wildcardMatch(it, permissionRequested) }) {
            logger.debug { "Explicitly allowed by: '${allows.first { wildcardMatch(it, permissionRequested) }}'" }
            return true
        }

        // If we could not reach decision yet, try referring to the parent
        if (parentGroupId == null) {
            logger.debug { "No parent group left" }
            return false
        }
        val parentGroup = groupTopicProcessor.getGroup(parentGroupId)
        if (parentGroup == null) {
            logger.warn("Group with id: '$parentGroupId' cannot be found")
            return false
        }
        val rolesIdsForGroup = parentGroup.roleIds
        return performCheckRec(rolesIdsForGroup, parentGroup.parentGroupId, permission)
    }

    private fun wildcardMatch(existingPermission: Permission, permissionRequested: String) : Boolean {
        return permissionRequested.matches(existingPermission.permissionString.toRegex())
    }
}