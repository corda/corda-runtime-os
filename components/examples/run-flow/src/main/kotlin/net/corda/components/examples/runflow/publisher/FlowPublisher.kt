package net.corda.components.examples.runflow.publisher

import com.typesafe.config.Config
import net.corda.data.client.rpc.flow.RPCFlowStart
import net.corda.data.crypto.SecureHash
import net.corda.data.flow.FlowKey
import net.corda.data.flow.event.FlowEvent
import net.corda.data.identity.HoldingIdentity
import net.corda.lifecycle.Lifecycle
import net.corda.lifecycle.LifecycleCoordinator
import net.corda.messaging.api.publisher.Publisher
import net.corda.messaging.api.publisher.config.PublisherConfig
import net.corda.messaging.api.publisher.factory.PublisherFactory
import net.corda.messaging.api.records.Record
import net.corda.v5.base.util.contextLogger
import org.osgi.service.component.annotations.Component
import org.slf4j.Logger
import java.nio.ByteBuffer


@Component
@Suppress("LongParameterList")
class FlowPublisher(
    private val lifeCycleCoordinator: LifecycleCoordinator,
    private val publisherFactory: PublisherFactory,
    private val instanceId: Int?,
    private val config: Config
) : Lifecycle {

    private var publisher: Publisher? = null

    companion object {
        val log: Logger = contextLogger()
        const val clientId = "publisher"
        const val publisherTopic = "PublisherTopic"
    }

    override var isRunning: Boolean = false

    override fun start() {
        if (!isRunning) {
            isRunning = true
            val pubConfig = PublisherConfig(clientId, instanceId)
            log.info("Instantiating publisher...")
            publisher = publisherFactory.createPublisher(pubConfig, config)

            val records = mutableListOf<Record<*, *>>()
            records.add(
                Record(
                    publisherTopic,
                    "flowStartEvent",
                    FlowEvent(
                        FlowKey("DemoFlow", HoldingIdentity("abcCorp", "testSpace")),
                        RPCFlowStart(
                            "abcCorp",
                            "demoUser",
                            SecureHash("algorithm", ByteBuffer.wrap("1".toByteArray())),
                            "DemoFlow",
                            listOf()
                        )
                    )
                )
            )
            log.info("Publishing records with key ")
            publisher?.publish(records)

            log.info("Publishing complete.")
            isRunning = false
            lifeCycleCoordinator.stop()
        }
    }

    override fun stop() {
        log.info("Stopping publisher")
        isRunning = false
        publisher?.close()
    }
}
