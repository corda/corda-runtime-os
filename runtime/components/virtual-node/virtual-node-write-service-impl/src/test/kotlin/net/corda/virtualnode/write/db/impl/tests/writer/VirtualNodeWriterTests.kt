package net.corda.virtualnode.write.db.impl.tests.writer

import net.corda.data.virtualnode.VirtualNodeAsynchronousRequest
import net.corda.data.virtualnode.VirtualNodeManagementRequest
import net.corda.data.virtualnode.VirtualNodeManagementResponse
import net.corda.messaging.api.publisher.Publisher
import net.corda.messaging.api.subscription.RPCSubscription
import net.corda.messaging.api.subscription.Subscription
import net.corda.virtualnode.write.db.impl.writer.VirtualNodeWriter
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.times
import org.mockito.kotlin.verify

/** Tests of [VirtualNodeWriter]. */
class VirtualNodeWriterTests {
    private val rpcSubscription = mock<RPCSubscription<VirtualNodeManagementRequest, VirtualNodeManagementResponse>>()
    private val durableSubscription = mock<Subscription<String, VirtualNodeAsynchronousRequest>>()
    private val publisher = mock<Publisher>()

    private val writer = VirtualNodeWriter(rpcSubscription, durableSubscription, publisher)

    @Test
    fun `the config writer's subscription and publisher are initially in an unstarted state`() {
        verify(rpcSubscription, times(0)).start()
        verify(durableSubscription, times(0)).start()
        verify(publisher, times(0)).start()
    }

    @Test
    fun `starting the config writer starts the subscription and publisher`() {
        writer.start()

        verify(rpcSubscription).start()
        verify(durableSubscription).start()
        verify(publisher).start()
    }

    @Test
    fun `stopping the virtual node writer stops the subscription and publisher`() {
        writer.start()
        writer.close()

        verify(rpcSubscription).close()
        verify(durableSubscription).close()
        verify(publisher).close()
    }
}
