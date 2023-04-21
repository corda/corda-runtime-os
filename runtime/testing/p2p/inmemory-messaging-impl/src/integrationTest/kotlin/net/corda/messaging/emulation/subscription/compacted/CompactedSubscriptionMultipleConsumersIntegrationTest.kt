package net.corda.messaging.emulation.subscription.compacted

import net.corda.libs.configuration.SmartConfigImpl
import net.corda.messaging.api.processor.CompactedProcessor
import net.corda.messaging.api.publisher.config.PublisherConfig
import net.corda.messaging.api.publisher.factory.PublisherFactory
import net.corda.messaging.api.records.Record
import net.corda.messaging.api.subscription.config.SubscriptionConfig
import net.corda.messaging.api.subscription.factory.SubscriptionFactory
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.osgi.test.common.annotation.InjectService
import org.osgi.test.junit5.service.ServiceExtension
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.CountDownLatch
import java.util.concurrent.atomic.AtomicReference

@ExtendWith(ServiceExtension::class)
class CompactedSubscriptionMultipleConsumersIntegrationTest {
    @InjectService(timeout = 4000)
    lateinit var subscriptionFactory: SubscriptionFactory

    @InjectService(timeout = 4000)
    lateinit var publisherFactory: PublisherFactory

    private val topic = "compacted.Topic"

    private val config = SmartConfigImpl.empty()

    private val snapshotSize = 5
    private val additionalData = 10

    private fun createSnapshot() {
        val publisherConfig = PublisherConfig("publisher.to.snapshot")
        val records = (1..snapshotSize).map {
            Record(topic, "key.$it", "value.0.$it")
        }

        publisherFactory.createPublisher(publisherConfig, config).use {
            it.publish(records).forEach { it.get() }
        }
    }

    private fun sendMoreData() {
        val publisherConfig = PublisherConfig("publisher.to.snapshot")
        val records = (1..additionalData).map {
            Record(topic, "key.$it", "value.1.$it")
        }

        publisherFactory.createPublisher(publisherConfig, config).use {
            it.publish(records).forEach { it.get() }
        }
    }

    data class Change(
        val newRecord: Record<String, String>,
        val oldValue: String?,
        val currentData: Map<String, String>
    )

    private inner class Processor : CompactedProcessor<String, String> {
        override val keyClass = String::class.java
        override val valueClass = String::class.java

        val snapshot = AtomicReference<Map<String, String>>()
        private val snapshotLatch = CountDownLatch(1)

        val changes = CopyOnWriteArrayList<Change>()
        private val changesLatch = CountDownLatch(additionalData)

        override fun onSnapshot(currentData: Map<String, String>) {
            snapshot.set(currentData)
            snapshotLatch.countDown()
        }

        override fun onNext(
            newRecord: Record<String, String>,
            oldValue: String?,
            currentData: Map<String, String>
        ) {
            changes.add(Change(newRecord, oldValue, currentData))
            changesLatch.countDown()
        }

        fun waitForSnapshot() {
            snapshotLatch.await()
        }

        fun waitForChanges() {
            changesLatch.await()
        }
    }

    @Test
    fun `test more than one consumer with compacted data`() {
        createSnapshot()

        val config = SubscriptionConfig("group", topic)
        val processor1 = Processor()
        val processor2 = Processor()

        val subscriber1 = subscriptionFactory.createCompactedSubscription(config, processor1, SmartConfigImpl.empty())
        val subscriber2 = subscriptionFactory.createCompactedSubscription(config, processor2, SmartConfigImpl.empty())
        subscriber1.start()
        subscriber2.start()

        processor1.waitForSnapshot()
        processor2.waitForSnapshot()

        assertThat(processor1.snapshot).hasValue(
            (1..snapshotSize).map {
                "key.$it" to "value.0.$it"
            }.toMap()
        )
        assertThat(processor2.snapshot).hasValue(
            (1..snapshotSize).map {
                "key.$it" to "value.0.$it"
            }.toMap()
        )
        assertThat(processor1.changes).isEmpty()
        assertThat(processor2.changes).isEmpty()

        sendMoreData()

        processor1.waitForChanges()
        processor2.waitForChanges()

        assertThat(processor1.changes).hasSize(additionalData)
        assertThat(processor2.changes).hasSize(additionalData)
    }
}
