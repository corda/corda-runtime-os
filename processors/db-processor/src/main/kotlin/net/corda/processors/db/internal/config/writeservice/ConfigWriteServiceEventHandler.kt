package net.corda.processors.db.internal.config.writeservice

import net.corda.data.permissions.management.PermissionManagementRequest
import net.corda.data.permissions.management.PermissionManagementResponse
import net.corda.libs.configuration.SmartConfig
import net.corda.lifecycle.LifecycleCoordinator
import net.corda.lifecycle.LifecycleEvent
import net.corda.lifecycle.LifecycleEventHandler
import net.corda.lifecycle.LifecycleStatus
import net.corda.lifecycle.StartEvent
import net.corda.lifecycle.StopEvent
import net.corda.messaging.api.publisher.Publisher
import net.corda.messaging.api.publisher.config.PublisherConfig
import net.corda.messaging.api.publisher.factory.PublisherFactory
import net.corda.messaging.api.subscription.RPCSubscription
import net.corda.messaging.api.subscription.factory.SubscriptionFactory
import net.corda.messaging.api.subscription.factory.config.RPCConfig
import net.corda.processors.db.internal.config.writer.ConfigWriterProcessor
import net.corda.processors.db.internal.db.DBWriter

class ConfigWriteServiceEventHandler(
    private val dbWriter: DBWriter,
    private val subscriptionFactory: SubscriptionFactory,
    private val publisherFactory: PublisherFactory
) : LifecycleEventHandler {

    private var instanceId: Int? = null
    private var bootstrapConfig: SmartConfig? = null
    private var subscription: RPCSubscription<PermissionManagementRequest, PermissionManagementResponse>? = null
    private var publisher: Publisher? = null

    override fun processEvent(event: LifecycleEvent, coordinator: LifecycleCoordinator) {
        when (event) {
            is StartEvent -> Unit // We cannot start until we have the required config.

            is BootstrapConfigEvent -> {
                // TODO - Joel - Introduce similar logic to config read service to be idempotent.
                instanceId = event.instanceId
                bootstrapConfig = event.config
                coordinator.postEvent(SubscribeEvent())
            }

            is SubscribeEvent -> {
                subscribe()
                // TODO - Joel - Should I spin here while waiting for DB to come up, if it's not up?
                coordinator.updateStatus(LifecycleStatus.UP)
            }

            is StopEvent -> {
                subscription?.stop()
                coordinator.updateStatus(LifecycleStatus.DOWN)
            }
        }
    }

    // TODO - Joel - This code should be encapsulated in another class so that we can use either a Kafka or non-Kafka impl.
    private fun subscribe() {
        val config = bootstrapConfig ?: throw ConfigWriteServiceException("TODO - Joel - Exception message.")
        if (subscription != null || publisher != null) throw ConfigWriteServiceException("TODO - Joel - Exception message.")

        val rpcConfig = RPCConfig(
            GROUP_NAME,
            CLIENT_NAME,
            CONFIG_UPDATE_REQUEST_TOPIC,
            // TODO - Joel - Define own Avro objects, instead of reusing these premade ones.
            PermissionManagementRequest::class.java,
            PermissionManagementResponse::class.java,
            instanceId
        )

        val publisher = publisherFactory.createPublisher(PublisherConfig(CLIENT_ID, instanceId), config)
        val configWriterProcessor = ConfigWriterProcessor(dbWriter, publisher)
        val subscription = subscriptionFactory.createRPCSubscription(rpcConfig, config, configWriterProcessor)

        this.subscription = subscription
        this.publisher = publisher

        subscription.start()
        publisher.start()
    }
}