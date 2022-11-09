package net.corda.messaging.subscription.consumer.listener

import net.corda.lifecycle.LifecycleStatus
import net.corda.messagebus.api.CordaTopicPartition
import net.corda.messaging.subscription.LifecycleStatusUpdater
import net.corda.messaging.utils.FutureTracker
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.times
import org.mockito.kotlin.verify

class RPCConsumerRebalanceListenerTest {

    @Test
    fun `Test up and down status changes are triggered correctly`() {
        val lifecycleStatusUpdater: LifecycleStatusUpdater = mock()
        val listener = RPCConsumerRebalanceListener<String>("test", "test", FutureTracker(), lifecycleStatusUpdater)

        listener.onPartitionsAssigned(mutableListOf(CordaTopicPartition("test", 0)))
        verify(lifecycleStatusUpdater, times(1)).updateLifecycleStatus(LifecycleStatus.UP)

        listener.onPartitionsRevoked(mutableListOf(CordaTopicPartition("test", 0)))
        verify(lifecycleStatusUpdater, times(1)).updateLifecycleStatus(LifecycleStatus.DOWN)
    }
}
