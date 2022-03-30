package net.corda.permissions.validation.cache

import java.util.concurrent.ConcurrentHashMap
import net.corda.configuration.read.ConfigChangedEvent
import net.corda.configuration.read.ConfigurationReadService
import net.corda.data.permissions.summary.UserPermissionSummary
import net.corda.libs.configuration.SmartConfig
import net.corda.libs.permissions.validation.cache.PermissionValidationCache
import net.corda.libs.permissions.validation.cache.factory.PermissionValidationCacheFactory
import net.corda.libs.permissions.validation.cache.factory.PermissionValidationCacheTopicProcessorFactory
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
import net.corda.messaging.api.config.toMessagingConfig
import net.corda.messaging.api.subscription.CompactedSubscription
import net.corda.messaging.api.subscription.config.SubscriptionConfig
import net.corda.messaging.api.subscription.factory.SubscriptionFactory
import net.corda.permissions.validation.cache.internal.PermissionSummaryTopicSnapshotReceived
import net.corda.schema.Schemas.Permissions.Companion.PERMISSIONS_USER_SUMMARY_TOPIC
import net.corda.schema.configuration.ConfigKeys.BOOT_CONFIG
import net.corda.schema.configuration.ConfigKeys.MESSAGING_CONFIG
import net.corda.v5.base.util.contextLogger
import org.osgi.service.component.annotations.Activate
import org.osgi.service.component.annotations.Component
import org.osgi.service.component.annotations.Reference

@Component(service = [PermissionValidationCacheService::class])
class PermissionValidationCacheService @Activate constructor(
    @Reference(service = SubscriptionFactory::class)
    private val subscriptionFactory: SubscriptionFactory,
    @Reference(service = LifecycleCoordinatorFactory::class)
    private val coordinatorFactory: LifecycleCoordinatorFactory,
    @Reference(service = PermissionValidationCacheFactory::class)
    private val permissionValidationCacheFactory: PermissionValidationCacheFactory,
    @Reference(service = PermissionValidationCacheTopicProcessorFactory::class)
    private val permissionValidationCacheTopicProcessorFactory: PermissionValidationCacheTopicProcessorFactory,
    @Reference(service = ConfigurationReadService::class)
    private val configurationReadService: ConfigurationReadService,
) : Lifecycle {

    private val coordinator = coordinatorFactory.createCoordinator<PermissionValidationCacheService> { event, _ -> eventHandler(event) }

    private companion object {
        val log = contextLogger()
        const val CONSUMER_GROUP = "PERMISSION_SERVICE"
    }

    /**
     * Instance of the cache used in this service.
     */
    val permissionValidationCache: PermissionValidationCache?
        get() {
            return _permissionValidationCache!!
        }

    private var _permissionValidationCache: PermissionValidationCache? = null

    private var permissionSummarySubscription: CompactedSubscription<String, UserPermissionSummary>? = null
    private var configHandle: AutoCloseable? = null

    private var configRegistration: RegistrationHandle? = null
    private var topicsRegistration: RegistrationHandle? = null

    private var permissionSummarySnapshotReceived: Boolean = false

    private fun eventHandler(event: LifecycleEvent) {
        when (event) {
            is StartEvent -> {
                log.info("Received start event, following configuration read service for status updates.")
                configRegistration?.close()
                configRegistration =
                    coordinator.followStatusChangesByName(
                        setOf(
                            LifecycleCoordinatorName.forComponent<ConfigurationReadService>()
                        )
                    )
            }
            is RegistrationStatusChangeEvent -> {
                log.info("Registration status change received: ${event.status.name}.")
                if (event.status == LifecycleStatus.UP) {
                    if (configHandle == null) {
                        log.info("Registering for configuration updates.")
                        configHandle = configurationReadService.registerComponentForUpdates(
                            coordinator, setOf(BOOT_CONFIG, MESSAGING_CONFIG))
                    }
                } else {
                    downTransition()
                }
            }
            is ConfigChangedEvent -> {
                createAndStartSubscriptionsAndCache(event.config.toMessagingConfig())
            }
            // Let's set the component as UP when it has received all the snapshots it needs
            is PermissionSummaryTopicSnapshotReceived -> {
                log.info("Permission summary topic snapshot received.")
                permissionSummarySnapshotReceived = true
                setStatusUp()
            }
            is StopEvent -> {
                log.info("Stop event received, stopping dependencies and setting status to DOWN.")
                configRegistration?.close()
                configRegistration = null
                downTransition()
                _permissionValidationCache?.stop()
                _permissionValidationCache = null
            }
        }
    }

    private fun downTransition() {
        coordinator.updateStatus(LifecycleStatus.DOWN)

        configHandle?.close()
        configHandle = null
        topicsRegistration?.close()
        topicsRegistration = null
        permissionSummarySubscription?.stop()
        permissionSummarySubscription = null
        permissionSummarySnapshotReceived = false
    }

    private fun setStatusUp() {
        log.info("Permission validation cache service has received all snapshots, setting status UP.")
        coordinator.updateStatus(LifecycleStatus.UP)
    }

    private fun createAndStartSubscriptionsAndCache(config: SmartConfig) {
        val permissionSummaryData = ConcurrentHashMap<String, UserPermissionSummary>()

        permissionSummarySubscription?.stop()
        val permissionSummarySubscription = createPermissionSummarySubscription(permissionSummaryData, config)
            .also {
                it.start()
                permissionSummarySubscription = it
            }

        topicsRegistration?.close()
        topicsRegistration = coordinator.followStatusChangesByName(setOf(permissionSummarySubscription.subscriptionName))

        _permissionValidationCache?.stop()
        _permissionValidationCache = permissionValidationCacheFactory.createPermissionValidationCache(permissionSummaryData)
            .also { it.start() }
    }

    private fun createPermissionSummarySubscription(
        permissionSummaryData: ConcurrentHashMap<String, UserPermissionSummary>,
        kafkaConfig: SmartConfig
    ): CompactedSubscription<String, UserPermissionSummary> {
        return subscriptionFactory.createCompactedSubscription(
            SubscriptionConfig(CONSUMER_GROUP, PERMISSIONS_USER_SUMMARY_TOPIC),
            permissionValidationCacheTopicProcessorFactory.createPermissionSummaryTopicProcessor(permissionSummaryData) {
                coordinator.postEvent(PermissionSummaryTopicSnapshotReceived())
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
