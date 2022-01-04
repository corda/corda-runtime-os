package net.corda.messaging.kafka.subscription.net.corda.messaging.kafka.subscription.consumer.listener

import net.corda.lifecycle.LifecycleCoordinator
import net.corda.lifecycle.LifecycleStatus
import net.corda.messagebus.api.CordaTopicPartition
import net.corda.messaging.kafka.utils.FutureTracker
import net.corda.messaging.subscription.consumer.listener.RPCConsumerRebalanceListener
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.times
import org.mockito.kotlin.verify

class RPCConsumerRebalanceListenerTest {

    @Test
    fun `Test up and down status changes are triggered correctly`() {
        val lifecycleCoordinator: LifecycleCoordinator = mock()
        val listener = RPCConsumerRebalanceListener<String>("test", "test", FutureTracker(), lifecycleCoordinator)

        listener.onPartitionsAssigned(mutableListOf(CordaTopicPartition("test", 0)))
        verify(lifecycleCoordinator, times(1)).updateStatus(LifecycleStatus.UP)

        listener.onPartitionsRevoked(mutableListOf(CordaTopicPartition("test", 0)))
        verify(lifecycleCoordinator, times(1)).updateStatus(LifecycleStatus.DOWN)
    }
}
