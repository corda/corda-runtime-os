package net.corda.libs.virtualnode.writer.impl.tests

import net.corda.libs.virtualnode.write.impl.VirtualNodeCreationRPCSubscription
import net.corda.libs.virtualnode.write.impl.VirtualNodeWriterImpl
import net.corda.messaging.api.publisher.Publisher
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

/** Tests of [VirtualNodeWriterImpl]. */
class VirtualNodeWriterImplTests {
    @Test
    fun `the config writer's subscription and publisher are initially in an unstarted state`() {
        val subscription = mock<VirtualNodeCreationRPCSubscription>()
        val publisher = mock<Publisher>()
        VirtualNodeWriterImpl(subscription, publisher)

        verify(subscription, times(0)).start()
        verify(publisher, times(0)).start()
    }

    @Test
    fun `starting the config writer starts the subscription and publisher`() {
        val subscription = mock<VirtualNodeCreationRPCSubscription>()
        val publisher = mock<Publisher>()
        val configWriter = VirtualNodeWriterImpl(subscription, publisher)
        configWriter.start()

        verify(subscription).start()
        verify(publisher).start()
    }

    @Test
    fun `stopping the config writer stops the subscription and publisher`() {
        val subscription = mock<VirtualNodeCreationRPCSubscription>()
        val publisher = mock<Publisher>()
        val configWriter = VirtualNodeWriterImpl(subscription, publisher)
        configWriter.start()
        configWriter.stop()

        verify(subscription).stop()
        verify(publisher).close()
    }

    @Test
    fun `the config writer is running if the subscription is running`() {
        val runningSubscription = mock<VirtualNodeCreationRPCSubscription>().apply {
            whenever(isRunning).thenReturn(true)
        }
        val configWriter = VirtualNodeWriterImpl(runningSubscription, mock())
        assertTrue(configWriter.isRunning)
    }

    @Test
    fun `the config writer is not running if the subscription is not running`() {
        val runningSubscription = mock<VirtualNodeCreationRPCSubscription>().apply {
            whenever(isRunning).thenReturn(false)
        }
        val configWriter = VirtualNodeWriterImpl(runningSubscription, mock())
        assertFalse(configWriter.isRunning)
    }
}