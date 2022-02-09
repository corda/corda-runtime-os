package net.corda.configuration.write.impl.tests.writer

import net.corda.configuration.write.impl.writer.ConfigWriter
import net.corda.configuration.write.impl.writer.ConfigurationManagementRPCSubscription
import net.corda.messaging.api.publisher.Publisher
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

/** Tests of [ConfigWriter]. */
class ConfigWriterTests {
    @Test
    fun `the config writer's subscription and publisher are initially in an unstarted state`() {
        val subscription = mock<ConfigurationManagementRPCSubscription>()
        val publisher = mock<Publisher>()
        ConfigWriter(subscription, publisher)

        verify(subscription, times(0)).start()
        verify(publisher, times(0)).start()
    }

    @Test
    fun `starting the config writer starts the subscription and publisher`() {
        val subscription = mock<ConfigurationManagementRPCSubscription>()
        val publisher = mock<Publisher>()
        val configWriter = ConfigWriter(subscription, publisher)
        configWriter.start()

        verify(subscription).start()
        verify(publisher).start()
    }

    @Test
    fun `stopping the config writer stops the subscription and publisher`() {
        val subscription = mock<ConfigurationManagementRPCSubscription>()
        val publisher = mock<Publisher>()
        val configWriter = ConfigWriter(subscription, publisher)
        configWriter.start()
        configWriter.stop()

        verify(subscription).stop()
        verify(publisher).close()
    }

    @Test
    fun `the config writer is running if the subscription is running`() {
        val runningSubscription = mock<ConfigurationManagementRPCSubscription>().apply {
            whenever(isRunning).thenReturn(true)
        }
        val configWriter = ConfigWriter(runningSubscription, mock())
        assertTrue(configWriter.isRunning)
    }

    @Test
    fun `the config writer is not running if the subscription is not running`() {
        val runningSubscription = mock<ConfigurationManagementRPCSubscription>().apply {
            whenever(isRunning).thenReturn(false)
        }
        val configWriter = ConfigWriter(runningSubscription, mock())
        assertFalse(configWriter.isRunning)
    }
}