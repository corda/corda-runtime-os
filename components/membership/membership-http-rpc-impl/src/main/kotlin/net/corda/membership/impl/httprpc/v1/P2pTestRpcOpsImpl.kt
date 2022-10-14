package net.corda.membership.impl.httprpc.v1

import net.corda.configuration.read.ConfigurationReadService
import net.corda.data.identity.HoldingIdentity
import net.corda.httprpc.PluggableRPCOps
import net.corda.libs.configuration.SmartConfig
import net.corda.lifecycle.Lifecycle
import net.corda.lifecycle.LifecycleCoordinatorFactory
import net.corda.lifecycle.LifecycleCoordinatorName
import net.corda.lifecycle.LifecycleStatus
import net.corda.lifecycle.Resource
import net.corda.membership.httprpc.v1.P2pTestRpcOps
import net.corda.membership.impl.httprpc.v1.lifecycle.RpcOpsLifecycleHandler
import net.corda.messaging.api.processor.DurableProcessor
import net.corda.messaging.api.publisher.config.PublisherConfig
import net.corda.messaging.api.publisher.factory.PublisherFactory
import net.corda.messaging.api.records.Record
import net.corda.messaging.api.subscription.config.SubscriptionConfig
import net.corda.messaging.api.subscription.factory.SubscriptionFactory
import net.corda.p2p.app.AppMessage
import net.corda.p2p.app.AuthenticatedMessage
import net.corda.p2p.app.AuthenticatedMessageHeader
import net.corda.schema.Schemas
import net.corda.schema.Schemas.P2P.Companion.P2P_OUT_TOPIC
import net.corda.schema.configuration.ConfigKeys
import net.corda.v5.base.exceptions.CordaRuntimeException
import org.osgi.service.component.annotations.Activate
import org.osgi.service.component.annotations.Component
import org.osgi.service.component.annotations.Reference
import java.nio.ByteBuffer
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

@Component(service = [PluggableRPCOps::class])
class P2pTestRpcOpsImpl @Activate constructor(
    @Reference(service = LifecycleCoordinatorFactory::class)
    lifecycleCoordinatorFactory: LifecycleCoordinatorFactory,
    @Reference(service = PublisherFactory::class)
    private val publisherFactory: PublisherFactory,
    @Reference(service = ConfigurationReadService::class)
    private val configurationReadService: ConfigurationReadService,
    @Reference(service = SubscriptionFactory::class)
    private val subscriptionFactory: SubscriptionFactory
) : P2pTestRpcOps, PluggableRPCOps<P2pTestRpcOps>, Lifecycle {
    companion object {
        private const val SUBSYSTEM = "P2pTestRpcOps"
    }
    private var configRegistration: Resource? = null
    private var messagingConfig: SmartConfig? = null
    override fun send(
        group: String,
        source: String,
        target: String,
        content: String
    ): String {
        return publisherFactory.createPublisher(
            PublisherConfig(SUBSYSTEM),
            messagingConfig ?: throw CordaRuntimeException("Not ready")
        ).use { publisher ->
            val messageId = UUID.randomUUID().toString()
            val message = AppMessage(
                AuthenticatedMessage(
                    AuthenticatedMessageHeader(
                        HoldingIdentity(
                            target,
                            group,
                        ),
                        HoldingIdentity(
                            source,
                            group,
                        ),
                        null,
                        messageId,
                        null,
                        SUBSYSTEM,
                    ),
                    ByteBuffer.wrap(content.toByteArray())
                )
            )
            val record = Record(P2P_OUT_TOPIC, messageId, message)
            publisher.publish(listOf(record)).forEach {
                it.join()
            }
            messageId
        }
    }

    override fun read(
        group: String,
        source: String,
        target: String,
        timeout: Int
    ): Map<String, String> {
        val messages = ConcurrentHashMap<String, String>()
        val processor = object : DurableProcessor<String, AppMessage> {
            override fun onNext(events: List<Record<String, AppMessage>>): List<Record<*, *>> {
                events.map { it.value }
                    .filterNotNull()
                    .map { it.message }
                    .filterIsInstance<AuthenticatedMessage>()
                    .filter { it.header.subsystem == SUBSYSTEM }
                    .map { it.header.messageId to String(it.payload.array()) }
                    .forEach { (id, content) -> messages[id] = content }
                return emptyList()
            }

            override val keyClass = String::class.java
            override val valueClass = AppMessage::class.java
        }
        subscriptionFactory.createDurableSubscription(
            SubscriptionConfig(
                "SUBSYSTEM-${UUID.randomUUID()}",
                Schemas.P2P.P2P_IN_TOPIC,
            ),
            processor,
            messagingConfig ?: throw CordaRuntimeException("Not ready"),
            null
        ).use { subscription ->
            subscription.start()
            Thread.sleep(timeout * 1000L)
        }

        return messages
    }

    override val targetInterface = P2pTestRpcOps::class.java

    override val protocolVersion = 1

    private val coordinatorName = LifecycleCoordinatorName.forComponent<P2pTestRpcOps>(
        protocolVersion.toString()
    )
    private fun updateStatus(status: LifecycleStatus, reason: String) {
        coordinator.updateStatus(status, reason)
    }

    private fun activate(reason: String) {
        updateStatus(LifecycleStatus.UP, reason)
        configRegistration?.close()
        configRegistration = configurationReadService.registerForUpdates { _, configs ->
            updateConfig(configs)
        }
    }

    private fun updateConfig(configs: Map<String, SmartConfig>) {
        if (configs.containsKey(ConfigKeys.MESSAGING_CONFIG)) {
            messagingConfig = configs[ConfigKeys.MESSAGING_CONFIG]
        }
    }

    private fun deactivate(reason: String) {
        configRegistration?.close()
        configRegistration = null
        updateStatus(LifecycleStatus.DOWN, reason)
    }

    private val lifecycleHandler = RpcOpsLifecycleHandler(
        ::activate,
        ::deactivate,
        setOf(
            LifecycleCoordinatorName.forComponent<ConfigurationReadService>(),
        )
    )
    private val coordinator = lifecycleCoordinatorFactory.createCoordinator(coordinatorName, lifecycleHandler)

    override val isRunning
        get() = coordinator.status == LifecycleStatus.UP

    override fun start() {
        coordinator.start()
    }

    override fun stop() {
        coordinator.stop()
    }
}
