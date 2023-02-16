package net.corda.permissions.storage.writer.internal

import net.corda.configuration.read.ConfigChangedEvent
import net.corda.configuration.read.ConfigurationReadService
import net.corda.data.permissions.management.PermissionManagementRequest
import net.corda.data.permissions.management.PermissionManagementResponse
import net.corda.libs.configuration.SmartConfig
import net.corda.libs.configuration.helper.getConfig
import net.corda.libs.permissions.storage.writer.factory.PermissionStorageWriterProcessorFactory
import net.corda.lifecycle.LifecycleCoordinator
import net.corda.lifecycle.LifecycleEvent
import net.corda.lifecycle.LifecycleEventHandler
import net.corda.lifecycle.LifecycleStatus
import net.corda.lifecycle.RegistrationStatusChangeEvent
import net.corda.lifecycle.StopEvent
import net.corda.messaging.api.subscription.RPCSubscription
import net.corda.messaging.api.subscription.config.RPCConfig
import net.corda.messaging.api.subscription.factory.SubscriptionFactory
import net.corda.permissions.storage.reader.PermissionStorageReaderService
import net.corda.schema.Schemas.Rest.Companion.REST_PERM_MGMT_REQ_TOPIC
import net.corda.schema.configuration.ConfigKeys.BOOT_CONFIG
import net.corda.schema.configuration.ConfigKeys.MESSAGING_CONFIG
import net.corda.utilities.VisibleForTesting
import org.slf4j.LoggerFactory
import javax.persistence.EntityManagerFactory

@Suppress("LongParameterList")
class PermissionStorageWriterServiceEventHandler(
    private val subscriptionFactory: SubscriptionFactory,
    private val permissionStorageWriterProcessorFactory: PermissionStorageWriterProcessorFactory,
    private val readerService: PermissionStorageReaderService,
    private val configurationReadService: ConfigurationReadService,
    // injecting factory creator so that this always fetches one from source rather than re-use one that may have been
    //   re-configured.
    private val entityManagerFactoryCreator: () -> EntityManagerFactory,
) : LifecycleEventHandler {

    private companion object {
        const val GROUP_NAME = "user.permissions.management"
        const val CLIENT_NAME = "user.permissions.management"

        val log = LoggerFactory.getLogger(this::class.java.enclosingClass)
    }

    @VisibleForTesting
    @Volatile
    internal var subscription: RPCSubscription<PermissionManagementRequest, PermissionManagementResponse>? = null

    @VisibleForTesting
    @Volatile
    internal var crsSub: AutoCloseable? = null

    override fun processEvent(event: LifecycleEvent, coordinator: LifecycleCoordinator) {
        when (event) {
            is RegistrationStatusChangeEvent -> {
                when (event.status) {
                    LifecycleStatus.UP -> {
                        crsSub = configurationReadService.registerComponentForUpdates(
                            coordinator,
                            setOf(BOOT_CONFIG, MESSAGING_CONFIG)
                        )
                    }
                    LifecycleStatus.DOWN -> {
                        downTransition(coordinator)
                    }
                    LifecycleStatus.ERROR -> {
                        coordinator.updateStatus(LifecycleStatus.ERROR)
                        coordinator.stop()
                    }
                }
            }
            is ConfigChangedEvent -> {
                onConfigurationUpdated(event.config.getConfig(MESSAGING_CONFIG))
                coordinator.updateStatus(LifecycleStatus.UP)
            }
            is StopEvent -> {
                downTransition(coordinator)
            }
        }
    }

    @VisibleForTesting
    internal fun onConfigurationUpdated(messagingConfig: SmartConfig) {
        subscription?.close()
        subscription = subscriptionFactory.createRPCSubscription(
            rpcConfig = RPCConfig(
                groupName = GROUP_NAME,
                clientName = CLIENT_NAME,
                requestTopic = REST_PERM_MGMT_REQ_TOPIC,
                requestType = PermissionManagementRequest::class.java,
                responseType = PermissionManagementResponse::class.java
            ),
            messagingConfig = messagingConfig,
            responderProcessor = permissionStorageWriterProcessorFactory.create(
                entityManagerFactoryCreator(), readerService::permissionStorageReader
            )
        ).also {
            it.start()
        }
    }

    private fun downTransition(coordinator: LifecycleCoordinator) {
        subscription?.close()
        subscription = null
        crsSub?.close()
        crsSub = null
        coordinator.updateStatus(LifecycleStatus.DOWN)
    }
}
