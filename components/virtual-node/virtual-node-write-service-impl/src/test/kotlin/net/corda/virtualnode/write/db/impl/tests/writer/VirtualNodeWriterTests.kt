package net.corda.virtualnode.write.db.impl.tests.writer

import net.corda.data.virtualnode.VirtualNodeManagementRequest
import net.corda.data.virtualnode.VirtualNodeManagementResponse
import net.corda.messaging.api.publisher.Publisher
import net.corda.messaging.api.subscription.RPCSubscription
import net.corda.virtualnode.write.db.impl.writer.VirtualNodeWriter
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

/** Tests of [VirtualNodeWriter]. */
class VirtualNodeWriterTests {
    @Test
    fun `the config writer's subscription and publisher are initially in an unstarted state`() {
        val subscription = mock<RPCSubscription<VirtualNodeManagementRequest, VirtualNodeManagementResponse>>()
        val publisher = mock<Publisher>()
        VirtualNodeWriter(subscription, publisher)

        verify(subscription, times(0)).start()
        verify(publisher, times(0)).start()
    }

    @Test
    fun `starting the config writer starts the subscription and publisher`() {
        val subscription = mock<RPCSubscription<VirtualNodeManagementRequest, VirtualNodeManagementResponse>>()
        val publisher = mock<Publisher>()
        val configWriter = VirtualNodeWriter(subscription, publisher)
        configWriter.start()

        verify(subscription).start()
        verify(publisher).start()
    }

    @Test
    fun `stopping the virtual node writer stops the subscription and publisher`() {
        val subscription = mock<RPCSubscription<VirtualNodeManagementRequest, VirtualNodeManagementResponse>>()
        val publisher = mock<Publisher>()
        val configWriter = VirtualNodeWriter(subscription, publisher)
        configWriter.start()
        configWriter.stop()

        verify(subscription).stop()
    }

    @Test
    fun `closing the virtual node writer closes the subscription and the publisher`() {
        val subscription = mock<RPCSubscription<VirtualNodeManagementRequest, VirtualNodeManagementResponse>>()
        val publisher = mock<Publisher>()
        val configWriter = VirtualNodeWriter(subscription, publisher)
        configWriter.start()
        configWriter.close()

        verify(subscription).close()
        verify(publisher).close()
    }

    @Test
    fun `the config writer is running if the subscription is running`() {
        val runningSubscription = mock<RPCSubscription<VirtualNodeManagementRequest, VirtualNodeManagementResponse>>()
            .apply {
                whenever(isRunning).thenReturn(true)
            }
        val configWriter = VirtualNodeWriter(runningSubscription, mock())
        assertTrue(configWriter.isRunning)
    }

    @Test
    fun `the config writer is not running if the subscription is not running`() {
        val runningSubscription = mock<RPCSubscription<VirtualNodeManagementRequest, VirtualNodeManagementResponse>>()
            .apply {
                whenever(isRunning).thenReturn(false)
            }
        val configWriter = VirtualNodeWriter(runningSubscription, mock())
        assertFalse(configWriter.isRunning)
    }
}
