package net.corda.components.examples.publisher

import net.corda.data.demo.DemoRecord
import net.corda.lifecycle.LifeCycle
import net.corda.messaging.api.publisher.Publisher
import net.corda.messaging.api.publisher.config.PublisherConfig
import net.corda.messaging.api.publisher.factory.PublisherFactory
import net.corda.messaging.api.records.Record
import org.osgi.service.component.annotations.Component
import org.slf4j.Logger
import org.slf4j.LoggerFactory

@Component
class RunPublisher (
    private val publisherFactory: PublisherFactory,
    private val instanceId: Int?
) : LifeCycle {

    private var publisherAsync: Publisher? = null
    private var publisherTransactional: Publisher? = null


    companion object {
        val log: Logger = LoggerFactory.getLogger(this::class.java)
        const val clientId = "publisher"
        const val numberOfRecords = 10
        const val publisherTopic = "publisherTopic"
    }

    override var isRunning: Boolean = false

    override fun start() {
        isRunning = true
        val pubConfigAsync = PublisherConfig(clientId)
        val pubConfigTransactional = PublisherConfig("$clientId-transactional", instanceId)

        val recordsAsync = mutableListOf<Record<*, *>>()
        for (i in 1..numberOfRecords) {
            recordsAsync.add(Record(publisherTopic, "key1", DemoRecord(i)))
        }

        val recordsTransactional = mutableListOf<Record<*, *>>()
        for (i in 1..numberOfRecords) {
            recordsTransactional.add(Record(publisherTopic, "key2", DemoRecord(i)))
        }

        log.info("Publishing Async records..")
        publisherAsync = publisherFactory.createPublisher(pubConfigAsync, mutableMapOf())
        publisherAsync?.publish(recordsAsync)

        log.info("Publishing transactional records..")
        publisherTransactional = publisherFactory.createPublisher(pubConfigTransactional, mutableMapOf())
        publisherTransactional?.publish(recordsTransactional)

        isRunning = false
    }

    override fun stop() {
        isRunning = false
        publisherAsync?.close()
        publisherTransactional?.close()
    }
}
