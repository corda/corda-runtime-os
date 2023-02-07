package net.corda.permissions.management.cache

import net.corda.configuration.read.ConfigChangedEvent
import net.corda.configuration.read.ConfigurationReadService
import net.corda.data.permissions.Group
import net.corda.data.permissions.Permission
import net.corda.data.permissions.Role
import net.corda.data.permissions.User
import net.corda.libs.configuration.SmartConfig
import net.corda.libs.configuration.helper.getConfig
import net.corda.libs.permissions.management.cache.PermissionManagementCache
import net.corda.libs.permissions.management.cache.events.GroupTopicSnapshotReceived
import net.corda.libs.permissions.management.cache.events.PermissionTopicSnapshotReceived
import net.corda.libs.permissions.management.cache.events.RoleTopicSnapshotReceived
import net.corda.libs.permissions.management.cache.events.UserTopicSnapshotReceived
import net.corda.libs.permissions.management.cache.factory.PermissionManagementCacheFactory
import net.corda.libs.permissions.management.cache.factory.PermissionManagementCacheTopicProcessorFactory
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
import net.corda.messaging.api.subscription.config.SubscriptionConfig
import net.corda.messaging.api.subscription.factory.SubscriptionFactory
import net.corda.schema.Schemas.Rest.REST_PERM_ENTITY_TOPIC
import net.corda.schema.Schemas.Rest.REST_PERM_GROUP_TOPIC
import net.corda.schema.Schemas.Rest.REST_PERM_ROLE_TOPIC
import net.corda.schema.Schemas.Rest.REST_PERM_USER_TOPIC
import net.corda.schema.configuration.ConfigKeys.BOOT_CONFIG
import net.corda.schema.configuration.ConfigKeys.MESSAGING_CONFIG
import org.osgi.service.component.annotations.Activate
import org.osgi.service.component.annotations.Component
import org.osgi.service.component.annotations.Reference
import org.slf4j.LoggerFactory
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicReference

@Component(service = [PermissionManagementCacheService::class])
class PermissionManagementCacheService @Activate constructor(
    @Reference(service = SubscriptionFactory::class)
    private val subscriptionFactory: SubscriptionFactory,
    @Reference(service = LifecycleCoordinatorFactory::class)
    private val coordinatorFactory: LifecycleCoordinatorFactory,
    @Reference(service = PermissionManagementCacheFactory::class)
    private val permissionManagementCacheFactory: PermissionManagementCacheFactory,
    @Reference(service = PermissionManagementCacheTopicProcessorFactory::class)
    private val permissionManagementCacheTopicProcessorFactory: PermissionManagementCacheTopicProcessorFactory,
    @Reference(service = ConfigurationReadService::class)
    private val configurationReadService: ConfigurationReadService,
) : Lifecycle {

    private val coordinator = coordinatorFactory.createCoordinator<PermissionManagementCacheService> { event, _ -> eventHandler(event) }

    private companion object {
        val log = LoggerFactory.getLogger(this::class.java.enclosingClass)
        const val CONSUMER_GROUP = "PERMISSION_MANAGEMENT_SERVICE"
        const val REGISTRATION = "REGISTRATION"
        const val CONFIG = "CONFIG"
    }

    /**
     * Permission management cache may not be instantly initialized
     * The call sites, however, should be able to get hold of the reference which eventually will
     * be set to a working instance
     */
    val permissionManagementCacheRef = AtomicReference<PermissionManagementCache?>(null)

    private var userSubscription: CompactedSubscription<String, User>? = null
    private var groupSubscription: CompactedSubscription<String, Group>? = null
    private var roleSubscription: CompactedSubscription<String, Role>? = null
    private var permissionSubscription: CompactedSubscription<String, Permission>? = null

    private var topicsRegistration: RegistrationHandle? = null

    private var userSnapshotReceived: Boolean = false
    private var groupSnapshotReceived: Boolean = false
    private var roleSnapshotReceived: Boolean = false
    private var permissionSnapshotReceived: Boolean = false

    /**
     * Caches need to be retained even between cycles of configuration change when underlying
     * subscriptions restart.
     */
    private val userData = ConcurrentHashMap<String, User>()
    private val groupData = ConcurrentHashMap<String, Group>()
    private val roleData = ConcurrentHashMap<String, Role>()
    private val permissionData = ConcurrentHashMap<String, Permission>()

    private fun allSnapshotsReceived(): Boolean = userSnapshotReceived && groupSnapshotReceived &&
            roleSnapshotReceived && permissionSnapshotReceived

    private fun eventHandler(event: LifecycleEvent) {
        when (event) {
            is StartEvent -> {
                log.info("Received start event, waiting for UP event from ConfigurationReadService.")
                coordinator.createManagedResource(REGISTRATION) {
                    coordinator.followStatusChangesByName(
                        setOf(
                            LifecycleCoordinatorName.forComponent<ConfigurationReadService>()
                        )
                    )
                }
            }
            is RegistrationStatusChangeEvent -> {
                log.info("Registration status change received: ${event.status.name}.")
                if (event.status == LifecycleStatus.UP) {
                    coordinator.createManagedResource(CONFIG)
                    {
                        log.info("Registering for configuration updates.")
                        configurationReadService.registerComponentForUpdates(
                            coordinator, setOf(BOOT_CONFIG, MESSAGING_CONFIG)
                        )
                    }
                }
                coordinator.updateStatus(event.status)
            }
            is ConfigChangedEvent -> {
                createAndStartSubscriptionsAndCache(event.config.getConfig(MESSAGING_CONFIG))
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
            is PermissionTopicSnapshotReceived -> {
                log.info("Permission topic snapshot received.")
                permissionSnapshotReceived = true
                if (allSnapshotsReceived()) setStatusUp()
            }
            is StopEvent -> {
                log.info("Stop event received, stopping dependencies and setting status to DOWN.")
                downTransition()
                permissionManagementCacheRef.get()?.stop()
                permissionManagementCacheRef.set(null)
            }
        }
    }

    private fun downTransition() {
        coordinator.updateStatus(LifecycleStatus.DOWN)

        userSubscription?.close()
        userSubscription = null
        groupSubscription?.close()
        groupSubscription = null
        roleSubscription?.close()
        roleSubscription = null
        permissionSubscription?.close()
        permissionSubscription = null

        userSnapshotReceived = false
        groupSnapshotReceived = false
        permissionSnapshotReceived = false
        roleSnapshotReceived = false
    }

    private fun setStatusUp() {
        log.info("Permission cache service has received all snapshots, setting status UP.")
        coordinator.updateStatus(LifecycleStatus.UP)
    }

    private fun createAndStartSubscriptionsAndCache(config: SmartConfig) {
        // It is important to close `topicsRegistration` ahead of closing `userSubscription`,
        // `groupSubscription`, etc. Failure to do so will cause `coordinator` to go into
        // error state as followed dependency will be closed whilst registration is still active.
        topicsRegistration?.close()

        userSubscription?.close()
        userSubscription = createUserSubscription(userData, config)
            .also {
                it.start()
            }

        groupSubscription?.close()
        groupSubscription = createGroupSubscription(groupData, config)
            .also {
                it.start()
            }

        roleSubscription?.close()
        roleSubscription = createRoleSubscription(roleData, config)
            .also {
                it.start()
            }

        permissionSubscription?.close()
        permissionSubscription = createPermissionSubscription(permissionData, config)
            .also {
                it.start()
            }

        topicsRegistration = coordinator.followStatusChangesByName(
            setOf(
                userSubscription!!.subscriptionName, groupSubscription!!.subscriptionName,
                roleSubscription!!.subscriptionName, permissionSubscription!!.subscriptionName
            )
        )

        permissionManagementCacheRef.get()?.stop()
        permissionManagementCacheRef.set(permissionManagementCacheFactory.createPermissionManagementCache(
            userData,
            groupData,
            roleData,
            permissionData
        )
            .also { it.start() })
    }

    private fun createUserSubscription(
        userData: ConcurrentHashMap<String, User>,
        kafkaConfig: SmartConfig
    ): CompactedSubscription<String, User> {
        return subscriptionFactory.createCompactedSubscription(
            SubscriptionConfig(CONSUMER_GROUP, REST_PERM_USER_TOPIC),
            permissionManagementCacheTopicProcessorFactory.createUserTopicProcessor(userData) {
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
            SubscriptionConfig(CONSUMER_GROUP, REST_PERM_GROUP_TOPIC),
            permissionManagementCacheTopicProcessorFactory.createGroupTopicProcessor(groupData) {
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
            SubscriptionConfig(CONSUMER_GROUP, REST_PERM_ROLE_TOPIC),
            permissionManagementCacheTopicProcessorFactory.createRoleTopicProcessor(roleData) {
                coordinator.postEvent(RoleTopicSnapshotReceived())
            },
            kafkaConfig
        )
    }

    private fun createPermissionSubscription(
        permissionData: ConcurrentHashMap<String, Permission>,
        kafkaConfig: SmartConfig
    ): CompactedSubscription<String, Permission> {
        return subscriptionFactory.createCompactedSubscription(
            SubscriptionConfig(CONSUMER_GROUP, REST_PERM_ENTITY_TOPIC),
            permissionManagementCacheTopicProcessorFactory.createPermissionTopicProcessor(permissionData) {
                coordinator.postEvent(PermissionTopicSnapshotReceived())
            },
            kafkaConfig
        )
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
