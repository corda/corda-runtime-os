package net.corda.components.examples.publisher

import com.typesafe.config.Config
import net.corda.data.demo.DemoRecord
import net.corda.data.flow.FlowKey
import net.corda.data.flow.event.FlowEvent
import net.corda.data.flow.event.StartRPCFlow
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
import java.time.Instant

@Suppress("LongParameterList")
class RunPublisher (
    private val lifeCycleCoordinator: LifecycleCoordinator,
    private val publisherFactory: PublisherFactory,
    private val instanceId: Int?,
    private val config: Config
    ) : Lifecycle {

    private var publisher: Publisher? = null

    companion object {
        val log: Logger = contextLogger()
        const val clientId = "publisher"
        const val publisherTopic = "StartRPCFlowTopic"
    }

    override var isRunning: Boolean = false

    override fun start() {
        if (!isRunning) {
            isRunning = true
            val pubConfig = PublisherConfig(clientId, instanceId)
            log.info("Instantiating publisher...")
            publisher = publisherFactory.createPublisher(pubConfig, config)

            val records  = listOf(Record(publisherTopic, getFlowEvent().flowKey, getFlowEvent()))
            publisher?.publish(records)?.get(0)?.get()
        }

        log.info("Publishing complete.")
        isRunning = false
        lifeCycleCoordinator.stop()
    }

    private fun getFlowEvent(): FlowEvent {
        val identity = HoldingIdentity("cname", "123")
        val key = FlowKey("1", identity)
        val rpcStartFlow = StartRPCFlow("clientID", "net.corda.linearstatesample.flows.HelloWorldFlow\$Initiator", "cpiId", identity, Instant.now(), emptyList())
        return FlowEvent(key, rpcStartFlow)
    }

    override fun stop() {
        isRunning = false
        publisher?.close()
    }
}
