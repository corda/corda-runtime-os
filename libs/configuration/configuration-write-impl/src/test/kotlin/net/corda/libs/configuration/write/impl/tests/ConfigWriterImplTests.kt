package net.corda.libs.configuration.write.impl.tests

import net.corda.libs.configuration.write.impl.ConfigMgmtRPCSubscription
import net.corda.libs.configuration.write.impl.ConfigWriterImpl
import net.corda.messaging.api.publisher.Publisher
import net.corda.messaging.api.records.Record
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/** Tests of [ConfigWriterImpl]. */
class ConfigWriterImplTests {
    @Test
    fun `the config writer's subscription and publisher begin in an unstarted state`() {
        val subscription = DummySubscription()
        val publisher = DummyPublisher()
        ConfigWriterImpl(subscription, publisher)

        assertFalse(subscription.isRunning)
        assertFalse(publisher.isStarted)
    }

    @Test
    fun `starting the config writer starts the subscription and publisher`() {
        val subscription = DummySubscription()
        val publisher = DummyPublisher()
        val configWriter = ConfigWriterImpl(subscription, publisher)

        configWriter.start()
        assertTrue(subscription.isRunning)
        assertTrue(publisher.isStarted)
    }

    @Test
    fun `stopping the config writer stops the subscription and publisher`() {
        val subscription = DummySubscription()
        val publisher = DummyPublisher()
        val configWriter = ConfigWriterImpl(subscription, publisher)

        configWriter.start()
        configWriter.stop()
        assertFalse(subscription.isRunning)
        assertFalse(publisher.isStarted)
    }

    @Test
    fun `the config writer is running if the subscription is running`() {
        val subscription = DummySubscription()
        val publisher = DummyPublisher()
        val configWriter = ConfigWriterImpl(subscription, publisher)

        assertFalse(configWriter.isRunning)

        configWriter.start()
        assertTrue(configWriter.isRunning)

        configWriter.stop()
        assertFalse(configWriter.isRunning)
    }

    /** A [ConfigMgmtRPCSubscription] that tracks whether the subscription has been started. */
    private class DummySubscription : ConfigMgmtRPCSubscription {
        override var isRunning = false

        override fun start() {
            isRunning = true
        }

        override fun stop() {
            isRunning = false
        }

        override val subscriptionName get() = throw NotImplementedError()
    }

    /** A [Publisher] that tracks whether it has been started. */
    private class DummyPublisher : Publisher {
        var isStarted = false

        override fun start() {
            isStarted = true
        }

        override fun close() {
            isStarted = false
        }

        override fun publishToPartition(records: List<Pair<Int, Record<*, *>>>) = throw NotImplementedError()

        override fun publish(records: List<Record<*, *>>) = throw NotImplementedError()
    }
}