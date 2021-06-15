package net.corda.components.examples.publisher

import net.corda.data.demo.DemoRecord
import net.corda.lifecycle.LifeCycle
import net.corda.lifecycle.LifeCycleCoordinator
import net.corda.lifecycle.StopEvent
import net.corda.messaging.api.publisher.Publisher
import net.corda.messaging.api.publisher.config.PublisherConfig
import net.corda.messaging.api.publisher.factory.PublisherFactory
import net.corda.messaging.api.records.Record
import org.osgi.service.component.annotations.Component
import org.slf4j.Logger
import org.slf4j.LoggerFactory


@Component
class RunPublisher (
    private val lifeCycleCoordinator: LifeCycleCoordinator,
    private val publisherFactory: PublisherFactory,
    private val instanceId: Int?,
    private val numberOfRecords: Int
) : LifeCycle {

    private var publisherAsync: Publisher? = null
    private var publisherTransactional: Publisher? = null

    companion object {
        val log: Logger = LoggerFactory.getLogger(this::class.java)
        const val clientId = "publisher"
        const val publisherTopic = "publisherTopic"
    }

    override var isRunning: Boolean = false

    override fun start() {
        isRunning = true
        val pubConfigAsync = PublisherConfig(clientId)
        val pubConfigTransactional = PublisherConfig("$clientId-transactional", instanceId)

        val recordsAsync1 = mutableListOf<Record<*, *>>()
        for (i in 1..numberOfRecords) {
            recordsAsync1.add(Record(publisherTopic, "key1", DemoRecord(i)))
        }

        val recordsTransactional1 = mutableListOf<Record<*, *>>()
        for (i in 1..numberOfRecords) {
            recordsTransactional1.add(Record(publisherTopic, "key2-transactional", DemoRecord(i)))
        }

        val recordsAsync2 = mutableListOf<Record<*, *>>()
        for (i in 1..numberOfRecords) {
            recordsAsync2.add(Record(publisherTopic, "key3", DemoRecord(i)))
        }

        val recordsTransactional2 = mutableListOf<Record<*, *>>()
        for (i in 1..numberOfRecords) {
            recordsTransactional2.add(Record(publisherTopic, "key4-transactional", DemoRecord(i)))
        }

        log.info("Instantiating async publisher...")
        publisherAsync = publisherFactory.createPublisher(pubConfigAsync, mutableMapOf())
        log.info("Instantiating transactional publisher...")
        publisherTransactional = publisherFactory.createPublisher(pubConfigTransactional, mutableMapOf())
        log.info("Publishing first async record batch...")
        publisherAsync?.publish(recordsAsync1)

        log.info("Publishing first transactional record batch...")
        publisherTransactional?.publish(recordsTransactional1)

        log.info("Sleeping for 2 seconds to allow for multiple polls to occur per subscription...")
        Thread.sleep(2000)

        log.info("Publishing second async record batch...")
        publisherAsync?.publish(recordsAsync2)
        log.info("Publishing second transactional record batch...")
        publisherTransactional?.publish(recordsTransactional2)
        log.info("Publishing complete.")
        isRunning = false
        lifeCycleCoordinator.postEvent(StopEvent)
    }

    override fun stop() {
        log.info("Stopping publisher")
        isRunning = false
        publisherAsync?.close()
        publisherTransactional?.close()
    }
}
