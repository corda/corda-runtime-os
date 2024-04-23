package net.corda.p2p.linkmanager.tracker

import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify

class DeliveryTrackerPartitionAssignmentListenerTest {
    private val states = mock<PartitionsStates>()

    private val listener = DeliveryTrackerPartitionAssignmentListener(states)

    @Test
    fun `onPartitionsAssigned call the partition states`() {
        listener.onPartitionsAssigned(
            listOf(
                "topic" to 4,
                "topic" to 16,
                "topic" to 8,
            ),
        )

        verify(states).loadPartitions(
            setOf(
                4,
                16,
                8,
            ),
        )
    }

    @Test
    fun `onPartitionsUnassigned call the partition states`() {
        listener.onPartitionsUnassigned(
            listOf(
                "topic" to 4,
                "topic" to 16,
                "topic" to 8,
            ),
        )

        verify(states).forgetPartitions(
            setOf(
                4,
                16,
                8,
            ),
        )
    }
}
