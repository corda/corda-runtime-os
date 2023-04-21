package net.corda.messaging.subscription.consumer.listener

import net.corda.lifecycle.LifecycleStatus
import net.corda.messagebus.api.CordaTopicPartition
import net.corda.messaging.subscription.LifecycleStatusUpdater
import net.corda.messaging.utils.FutureTracker
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.times
import org.mockito.kotlin.verify

class RPCConsumerRebalanceListenerTest {
    private lateinit var lifecycleStatusUpdater: LifecycleStatusUpdater
    private lateinit var listener : RPCConsumerRebalanceListener<String>
    private val firstTopicPartition = CordaTopicPartition("test", 0)
    private val secondTopicPartition = CordaTopicPartition("test", 1)

    @BeforeEach
    fun setup() {
        lifecycleStatusUpdater = mock()
        listener = RPCConsumerRebalanceListener<String>("", "", FutureTracker(), lifecycleStatusUpdater)
    }

    @Test
    fun `Test up and down status changes are triggered correctly`() {
        val lifecycleStatusUpdater: LifecycleStatusUpdater = mock()
        val listener = RPCConsumerRebalanceListener<String>("test", "test", FutureTracker(), lifecycleStatusUpdater)

        listener.onPartitionsAssigned(mutableListOf(CordaTopicPartition("test", 0)))
        verify(lifecycleStatusUpdater, times(1)).updateLifecycleStatus(LifecycleStatus.UP)

        listener.onPartitionsRevoked(mutableListOf(CordaTopicPartition("test", 0)))
        verify(lifecycleStatusUpdater, times(1)).updateLifecycleStatus(LifecycleStatus.DOWN)
    }

    @Test
    fun `sets status to DOWN when there are no partitions assigned`() {
        listener.onPartitionsAssigned(mutableListOf(firstTopicPartition, secondTopicPartition))
        verify(lifecycleStatusUpdater, times(1)).updateLifecycleStatus(LifecycleStatus.UP)

        listener.onPartitionsRevoked(mutableListOf(firstTopicPartition))
        verify(lifecycleStatusUpdater, never()).updateLifecycleStatus(LifecycleStatus.DOWN)

        listener.onPartitionsRevoked(mutableListOf(secondTopicPartition))
        verify(lifecycleStatusUpdater, times(1)).updateLifecycleStatus(LifecycleStatus.DOWN)
    }

    @Test
    fun `sets status to UP when there is at least one partitions assigned`() {
        listener.onPartitionsAssigned(mutableListOf())
        verify(lifecycleStatusUpdater, never()).updateLifecycleStatus(LifecycleStatus.UP)

        listener.onPartitionsAssigned(mutableListOf(firstTopicPartition))
        verify(lifecycleStatusUpdater, times(1)).updateLifecycleStatus(LifecycleStatus.UP)
    }
}
