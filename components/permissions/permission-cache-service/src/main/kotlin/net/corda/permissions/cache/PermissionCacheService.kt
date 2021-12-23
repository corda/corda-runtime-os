package net.corda.permissions.cache

import net.corda.configuration.read.ConfigurationReadService
import net.corda.data.permissions.Group
import net.corda.data.permissions.Role
import net.corda.data.permissions.User
import net.corda.libs.configuration.SmartConfig
import net.corda.libs.permissions.cache.PermissionCache
import net.corda.libs.permissions.cache.events.GroupTopicSnapshotReceived
import net.corda.libs.permissions.cache.events.RoleTopicSnapshotReceived
import net.corda.libs.permissions.cache.events.UserTopicSnapshotReceived
import net.corda.libs.permissions.cache.factory.PermissionCacheFactory
import net.corda.libs.permissions.cache.factory.PermissionCacheTopicProcessorFactory
import net.corda.lifecycle.Lifecycle
import net.corda.lifecycle.LifecycleCoordinatorFactory
import net.corda.lifecycle.LifecycleCoordinatorName
import net.corda.lifecycle.LifecycleEvent
import net.corda.lifecycle.LifecycleStatus
import net.corda.lifecycle.RegistrationHandle
import net.corda.lifecycle.RegistrationStatusChangeEvent
import net.corda.lifecycle.StartEvent
import net.corda.lifecycle.StopEvent
import net.corda.lifecycle.createCoordinator
import net.corda.messaging.api.subscription.CompactedSubscription
import net.corda.messaging.api.subscription.factory.SubscriptionFactory
import net.corda.messaging.api.subscription.factory.config.SubscriptionConfig
import net.corda.schema.Schemas.RPC.Companion.RPC_PERM_GROUP_TOPIC
import net.corda.schema.Schemas.RPC.Companion.RPC_PERM_ROLE_TOPIC
import net.corda.schema.Schemas.RPC.Companion.RPC_PERM_USER_TOPIC
import net.corda.v5.base.util.contextLogger
import org.osgi.service.component.annotations.Activate
import org.osgi.service.component.annotations.Component
import org.osgi.service.component.annotations.Reference
import java.util.concurrent.ConcurrentHashMap

private data class NewConfigurationReceivedEvent(val config: SmartConfig): LifecycleEvent

@Component(service = [PermissionCacheService::class])
class PermissionCacheService @Activate constructor(
    @Reference(service = SubscriptionFactory::class)
    private val subscriptionFactory: SubscriptionFactory,
    @Reference(service = LifecycleCoordinatorFactory::class)
    private val coordinatorFactory: LifecycleCoordinatorFactory,
    @Reference(service = PermissionCacheFactory::class)
    private val permissionCacheFactory: PermissionCacheFactory,
    @Reference(service = PermissionCacheTopicProcessorFactory::class)
    private val permissionCacheTopicProcessorFactory: PermissionCacheTopicProcessorFactory,
    @Reference(service = ConfigurationReadService::class)
    private val configurationReadService: ConfigurationReadService,
) : Lifecycle {

    private val coordinator = coordinatorFactory.createCoordinator<PermissionCacheService> { event, _ -> eventHandler(event) }

    private companion object {
        val log = contextLogger()
        const val CONSUMER_GROUP = "PERMISSION_SERVICE"
        const val BOOTSTRAP_CONFIG = "corda.boot"
    }

    val permissionCache: PermissionCache?
        get() {
            return _permissionCache
        }
    private var _permissionCache: PermissionCache? = null
    private var userSubscription: CompactedSubscription<String, User>? = null
    private var groupSubscription: CompactedSubscription<String, Group>? = null
    private var roleSubscription: CompactedSubscription<String, Role>? = null
    private var configHandle: AutoCloseable? = null
    private var registration: RegistrationHandle? = null

    private var userSnapshotReceived: Boolean = false
    private var groupSnapshotReceived: Boolean = false
    private var roleSnapshotReceived: Boolean = false

    private fun allSnapshotsReceived(): Boolean = userSnapshotReceived && groupSnapshotReceived && roleSnapshotReceived

    private fun eventHandler(event: LifecycleEvent) {
        when (event) {
            is StartEvent -> {
                log.info("Received start event, waiting for UP event from ConfigurationReadService.")
                registration?.close()
                registration =
                    coordinator.followStatusChangesByName(
                        setOf(
                            LifecycleCoordinatorName.forComponent<ConfigurationReadService>()
                        )
                    )
            }
            is RegistrationStatusChangeEvent -> {
                log.info("Registration status change received for ConfigurationReadService: ${event.status.name}.")
                if (event.status == LifecycleStatus.UP) {
                    log.info("Registering for configuration updates.")
                    configHandle = configurationReadService.registerForUpdates(::onConfigChange)
                } else {
                    configHandle?.close()
                }
            }
            is NewConfigurationReceivedEvent -> {
                createAndStartSubscriptionsAndCache(event.config)
            }
            // Let's set the component as UP when it has received all the snapshots it needs
            is UserTopicSnapshotReceived -> {
                log.info("User topic snapshot received.")
                userSnapshotReceived = true
                if (allSnapshotsReceived()) setStatusUp()
            }
            is GroupTopicSnapshotReceived -> {
                log.info("Group topic snapshot received.")
                groupSnapshotReceived = true
                if (allSnapshotsReceived()) setStatusUp()
            }
            is RoleTopicSnapshotReceived -> {
                log.info("Role topic snapshot received.")
                roleSnapshotReceived = true
                if (allSnapshotsReceived()) setStatusUp()
            }
            is StopEvent -> {
                log.info("Stop event received, stopping dependencies and setting status to DOWN.")
                configHandle?.close()
                configHandle = null
                userSubscription?.stop()
                userSubscription = null
                groupSubscription?.stop()
                groupSubscription = null
                roleSubscription?.stop()
                roleSubscription = null
                _permissionCache?.stop()
                _permissionCache = null
                userSnapshotReceived = false
                groupSnapshotReceived = false
                roleSnapshotReceived = false
                coordinator.updateStatus(LifecycleStatus.DOWN)
            }
        }
    }

    private fun setStatusUp() {
        log.info("Permission cache service has received all snapshots, setting status UP.")
        coordinator.updateStatus(LifecycleStatus.UP)
    }

    private fun createAndStartSubscriptionsAndCache(config: SmartConfig) {
        val userData = ConcurrentHashMap<String, User>()
        val groupData = ConcurrentHashMap<String, Group>()
        val roleData = ConcurrentHashMap<String, Role>()
        userSubscription = createUserSubscription(userData, config)
            .also { it.start() }
        groupSubscription = createGroupSubscription(groupData, config)
            .also { it.start() }
        roleSubscription = createRoleSubscription(roleData, config)
            .also { it.start() }
        _permissionCache = permissionCacheFactory.createPermissionCache(userData, groupData, roleData)
            .also { it.start() }
    }

    private fun onConfigChange(changedKeys: Set<String>, config: Map<String, SmartConfig>) {
        log.info("Received configuration update event, changedKeys: $changedKeys")
        if (BOOTSTRAP_CONFIG in changedKeys){
            coordinator.postEvent(NewConfigurationReceivedEvent(config[BOOTSTRAP_CONFIG]!!))
        }
    }

    private fun createUserSubscription(
        userData: ConcurrentHashMap<String, User>,
        kafkaConfig: SmartConfig
    ): CompactedSubscription<String, User> {
        return subscriptionFactory.createCompactedSubscription(
            SubscriptionConfig(CONSUMER_GROUP, RPC_PERM_USER_TOPIC),
            permissionCacheTopicProcessorFactory.createUserTopicProcessor(userData) {
                coordinator.postEvent(UserTopicSnapshotReceived())
            },
            kafkaConfig
        )
    }

    private fun createGroupSubscription(
        groupData: ConcurrentHashMap<String, Group>,
        kafkaConfig: SmartConfig
    ): CompactedSubscription<String, Group> {
        return subscriptionFactory.createCompactedSubscription(
            SubscriptionConfig(CONSUMER_GROUP, RPC_PERM_GROUP_TOPIC),
            permissionCacheTopicProcessorFactory.createGroupTopicProcessor(groupData) {
                coordinator.postEvent(GroupTopicSnapshotReceived())
            },
            kafkaConfig
        )
    }

    private fun createRoleSubscription(
        roleData: ConcurrentHashMap<String, Role>,
        kafkaConfig: SmartConfig
    ): CompactedSubscription<String, Role> {
        return subscriptionFactory.createCompactedSubscription(
            SubscriptionConfig(CONSUMER_GROUP, RPC_PERM_ROLE_TOPIC),
            permissionCacheTopicProcessorFactory.createRoleTopicProcessor(roleData) {
                coordinator.postEvent(RoleTopicSnapshotReceived())
            },
            kafkaConfig
        )
    }

    override val isRunning: Boolean
        get() = coordinator.isRunning

    override fun start() {
        coordinator.start()
        coordinator.postEvent(StartEvent())
    }

    override fun stop() {
        coordinator.postEvent(StopEvent())
        coordinator.stop()
    }
}