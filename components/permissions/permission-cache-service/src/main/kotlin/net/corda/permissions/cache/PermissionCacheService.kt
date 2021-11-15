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
import net.corda.libs.permissions.cache.factory.PermissionCacheProcessorFactory
import net.corda.lifecycle.Lifecycle
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
import org.osgi.service.component.annotations.Activate
import org.osgi.service.component.annotations.Component
import org.osgi.service.component.annotations.Reference

@Component(service = [PermissionCacheService::class])
class PermissionCacheService @Activate constructor(
    @Reference(service = SubscriptionFactory::class)
    private val subscriptionFactory: SubscriptionFactory,
    @Reference(service = LifecycleCoordinatorFactory::class)
    private val coordinatorFactory: LifecycleCoordinatorFactory,
    @Reference(service = PermissionCacheFactory::class)
    private val permissionCacheFactory: PermissionCacheFactory,
    @Reference(service = PermissionCacheProcessorFactory::class)
    private val permissionCacheProcessorFactory: PermissionCacheProcessorFactory,
) : Lifecycle {

    private val coordinator = coordinatorFactory.createCoordinator<PermissionCacheService> { event, _ -> eventHandler(event) }

    private companion object {
        const val CONSUMER_GROUP = "PERMISSION_SERVICE"
    }

    val permissionCache: PermissionCache?
        get() {
            return _permissionCache
        }
    private var _permissionCache: PermissionCache? = null
    private var userSubscription: CompactedSubscription<String, User>? = null
    private var groupSubscription: CompactedSubscription<String, Group>? = null
    private var roleSubscription: CompactedSubscription<String, Role>? = null

    private var userSnapshotReceived: Boolean = false
    private var groupSnapshotReceived: Boolean = false
    private var roleSnapshotReceived: Boolean = false

    private fun allSnapshotsReceived(): Boolean = userSnapshotReceived && groupSnapshotReceived && roleSnapshotReceived

    private fun eventHandler(event: LifecycleEvent) {
        when (event) {
            is StartEvent -> {
                val userData = ConcurrentHashMap<String, User>()
                val groupData = ConcurrentHashMap<String, Group>()
                val roleData = ConcurrentHashMap<String, Role>()
                userSubscription = subscriptionFactory.createCompactedSubscription(
                    SubscriptionConfig(CONSUMER_GROUP, Schema.RPC_PERM_USER_TOPIC),
                    permissionCacheProcessorFactory.createUserProcessor(coordinator, userData)
                ).also { it.start() }
                groupSubscription = subscriptionFactory.createCompactedSubscription(
                    SubscriptionConfig(CONSUMER_GROUP, Schema.RPC_PERM_GROUP_TOPIC),
                    permissionCacheProcessorFactory.createGroupProcessor(coordinator, groupData)
                ).also { it.start() }
                roleSubscription = subscriptionFactory.createCompactedSubscription(
                    SubscriptionConfig(CONSUMER_GROUP, Schema.RPC_PERM_ROLE_TOPIC),
                    permissionCacheProcessorFactory.createRoleProcessor(coordinator, roleData)
                ).also { it.start() }
                _permissionCache = permissionCacheFactory.createPermissionCache(userData, groupData, roleData)
                    .also { it.start() }
            }
            // Let's set the component as UP when it has received all the snapshots it needs
            is UserTopicSnapshotReceived -> {
                userSnapshotReceived = true
                if (allSnapshotsReceived()) coordinator.updateStatus(LifecycleStatus.UP)
            }
            is GroupTopicSnapshotReceived -> {
                groupSnapshotReceived = true
                if (allSnapshotsReceived()) coordinator.updateStatus(LifecycleStatus.UP)
            }
            is RoleTopicSnapshotReceived -> {
                roleSnapshotReceived = true
                if (allSnapshotsReceived()) coordinator.updateStatus(LifecycleStatus.UP)
            }
            is StopEvent -> {
                userSubscription?.stop()
                groupSubscription?.stop()
                roleSubscription?.stop()
                userSubscription = null
                groupSubscription = null
                roleSubscription = null
                _permissionCache?.stop()
                userSnapshotReceived = false
                groupSnapshotReceived = false
                roleSnapshotReceived = false
                coordinator.updateStatus(LifecycleStatus.DOWN)
            }
        }
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