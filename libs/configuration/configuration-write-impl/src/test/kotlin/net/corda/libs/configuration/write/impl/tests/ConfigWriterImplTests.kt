package net.corda.libs.configuration.write.impl.tests

import net.corda.libs.configuration.write.impl.ConfigWriterImpl
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/** Tests of [ConfigWriterImpl]. */
class ConfigWriterImplTests {
    @Test
    fun `the config writer's subscription and publisher begin in an unstarted state`() {
        val subscription = DummyRPCSubscription()
        val publisher = DummyPublisher()
        ConfigWriterImpl(subscription, publisher)

        assertFalse(subscription.isRunning)
        assertFalse(publisher.isStarted)
    }

    @Test
    fun `starting the config writer starts the subscription and publisher`() {
        val subscription = DummyRPCSubscription()
        val publisher = DummyPublisher()
        val configWriter = ConfigWriterImpl(subscription, publisher)

        configWriter.start()
        assertTrue(subscription.isRunning)
        assertTrue(publisher.isStarted)
    }

    @Test
    fun `stopping the config writer stops the subscription and publisher`() {
        val subscription = DummyRPCSubscription()
        val publisher = DummyPublisher()
        val configWriter = ConfigWriterImpl(subscription, publisher)

        configWriter.start()
        configWriter.stop()
        assertFalse(subscription.isRunning)
        assertFalse(publisher.isStarted)
    }

    @Test
    fun `the config writer is running if the subscription is running`() {
        val subscription = DummyRPCSubscription()
        val publisher = DummyPublisher()
        val configWriter = ConfigWriterImpl(subscription, publisher)

        assertFalse(configWriter.isRunning)

        configWriter.start()
        assertTrue(configWriter.isRunning)

        configWriter.stop()
        assertFalse(configWriter.isRunning)
    }
}