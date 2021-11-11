package net.corda.permissions.cache

import java.util.concurrent.ConcurrentHashMap
import net.corda.data.permissions.Group
import net.corda.data.permissions.Role
import net.corda.data.permissions.User
import net.corda.libs.permissions.cache.PermissionCache
import net.corda.libs.permissions.cache.events.GroupTopicSnapshotReceived
import net.corda.libs.permissions.cache.events.RoleTopicSnapshotReceived
import net.corda.libs.permissions.cache.events.UserTopicSnapshotReceived
import net.corda.libs.permissions.cache.factory.PermissionCacheFactory
import net.corda.libs.permissions.cache.impl.GroupTopicProcessor
import net.corda.libs.permissions.cache.impl.RoleTopicProcessor
import net.corda.libs.permissions.cache.impl.UserTopicProcessor
import net.corda.lifecycle.Lifecycle
import net.corda.lifecycle.LifecycleCoordinator
import net.corda.lifecycle.LifecycleCoordinatorFactory
import net.corda.lifecycle.LifecycleEvent
import net.corda.lifecycle.LifecycleStatus
import net.corda.lifecycle.StartEvent
import net.corda.lifecycle.StopEvent
import net.corda.lifecycle.createCoordinator
import net.corda.messaging.api.subscription.CompactedSubscription
import net.corda.messaging.api.subscription.factory.SubscriptionFactory
import net.corda.messaging.api.subscription.factory.config.SubscriptionConfig
import net.corda.rpc.schema.Schema
import org.osgi.service.component.annotations.Component
import org.osgi.service.component.annotations.Reference

@Component(service = [PermissionCacheComponent::class])
class PermissionCacheComponent(
    @Reference(service = SubscriptionFactory::class)
    private val subscriptionFactory: SubscriptionFactory,
    @Reference(service = LifecycleCoordinatorFactory::class)
    private val coordinatorFactory: LifecycleCoordinatorFactory,
    @Reference(service = PermissionCacheFactory::class)
    private val permissionCacheFactory: PermissionCacheFactory,
    ) : Lifecycle {

    private val coordinator = coordinatorFactory.createCoordinator<PermissionCacheComponent>(::eventHandler)

    private companion object {
        const val CONSUMER_GROUP = "PERMISSION_SERVICE"
    }

    private var permissionCache: PermissionCache? = null
    private var userSubscription: CompactedSubscription<String, User>? = null
    private var groupSubscription: CompactedSubscription<String, Group>? = null
    private var roleSubscription: CompactedSubscription<String, Role>? = null
    private var userTopicProcessor: UserTopicProcessor? = null
    private var groupTopicProcessor: GroupTopicProcessor? = null
    private var roleTopicProcessor: RoleTopicProcessor? = null

    private var userSnapshotReceived: Boolean = false
    private var groupSnapshotReceived: Boolean = false
    private var roleSnapshotReceived: Boolean = false

    private var allSnapshotsReceived = userSnapshotReceived && groupSnapshotReceived && roleSnapshotReceived

    private fun eventHandler(event: LifecycleEvent, lifecycleCoordinator: LifecycleCoordinator) {
        when (event) {
            is StartEvent -> {
                val userData = ConcurrentHashMap<String, User>()
                val groupData = ConcurrentHashMap<String, Group>()
                val roleData = ConcurrentHashMap<String, Role>()
                userTopicProcessor = UserTopicProcessor(coordinator, userData)
                groupTopicProcessor = GroupTopicProcessor(coordinator, groupData)
                roleTopicProcessor = RoleTopicProcessor(coordinator, roleData)
                userSubscription = subscriptionFactory.createCompactedSubscription(
                    SubscriptionConfig(CONSUMER_GROUP, Schema.RPC_PERM_USER_TOPIC),
                    userTopicProcessor!!
                ).also { it.start() }
                groupSubscription = subscriptionFactory.createCompactedSubscription(
                    SubscriptionConfig(CONSUMER_GROUP, Schema.RPC_PERM_GROUP_TOPIC),
                    groupTopicProcessor!!
                ).also { it.start() }
                roleSubscription = subscriptionFactory.createCompactedSubscription(
                    SubscriptionConfig(CONSUMER_GROUP, Schema.RPC_PERM_ROLE_TOPIC),
                    roleTopicProcessor!!
                ).also { it.start() }
                permissionCache = permissionCacheFactory.createPermissionCache(userData, groupData, roleData)
                    .also { it.start() }
            }
            // Let's set the component as UP when it has received all the snapshots it needs
            is UserTopicSnapshotReceived -> {
                userSnapshotReceived = true
                if(allSnapshotsReceived) coordinator.updateStatus(LifecycleStatus.UP)
            }
            is GroupTopicSnapshotReceived -> {
                groupSnapshotReceived = true
                if(allSnapshotsReceived) coordinator.updateStatus(LifecycleStatus.UP)
            }
            is RoleTopicSnapshotReceived -> {
                roleSnapshotReceived = true
                if(allSnapshotsReceived) coordinator.updateStatus(LifecycleStatus.UP)
            }
            is StopEvent -> {
                userSubscription?.stop()
                groupSubscription?.stop()
                roleSubscription?.stop()
                permissionCache?.stop()
                userSnapshotReceived = false
                groupSnapshotReceived = false
                roleSnapshotReceived = false
                coordinator.updateStatus(LifecycleStatus.DOWN)
            }
        }
    }

    /**
     * Expose the permission cache.
     */
    fun getPermissionCache(): PermissionCache? {
        return permissionCache
    }

    override val isRunning: Boolean
        get() = coordinator.isRunning

    override fun start() {
        coordinator.start()
    }

    override fun stop() {
        coordinator.stop()
    }
}