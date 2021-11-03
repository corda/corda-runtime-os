package net.corda.components.examples.publisher

import net.corda.data.demo.DemoRecord
import net.corda.libs.configuration.SmartConfig
import net.corda.lifecycle.Lifecycle
import net.corda.lifecycle.LifecycleCoordinator
import net.corda.messaging.api.publisher.Publisher
import net.corda.messaging.api.publisher.config.PublisherConfig
import net.corda.messaging.api.publisher.factory.PublisherFactory
import net.corda.messaging.api.records.Record
import net.corda.v5.base.util.contextLogger
import org.osgi.service.component.annotations.Component
import org.slf4j.Logger


@Component
@Suppress("LongParameterList")
class RunPublisher (
    private val lifeCycleCoordinator: LifecycleCoordinator,
    private val publisherFactory: PublisherFactory,
    private val instanceId: Int?,
    private val numberOfRecords: Int,
    private val numberOfKeys: Int,
    private val config: SmartConfig
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

            for (i in 1..numberOfKeys) {
                val records = mutableListOf<Record<*, *>>()
                val key = "key$i"
                for (j in 1..numberOfRecords) {
                    records.add(Record(publisherTopic, key, DemoRecord(j)))
                }
                log.info("Publishing records with key $key...")
                publisher?.publish(records)
            }

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
