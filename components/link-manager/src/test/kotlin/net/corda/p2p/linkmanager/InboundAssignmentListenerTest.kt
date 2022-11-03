package net.corda.p2p.linkmanager

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

class InboundAssignmentListenerTest {

    private val listener = InboundAssignmentListener("topic")

    @Nested
    inner class OnPartitionsUnassignedTests {
        @Test
        fun `onPartitionsUnassigned will use only the correct topic`() {
            listener.onPartitionsAssigned(
                listOf(
                    "topic" to 4,
                    "topic" to 5,
                    "topic" to 6,
                    "topic" to 7,
                )
            )
            listener.onPartitionsUnassigned(
                listOf(
                    "topic" to 5,
                    "anotherTopic" to 4,
                    "topic" to 7,
                )
            )

            assertThat(listener.getCurrentlyAssignedPartitions()).containsExactly(4, 6)
        }

        @Test
        fun `onPartitionsUnassigned will change the status if all partitions had been unassigned`() {
            listener.onPartitionsAssigned(
                listOf(
                    "topic" to 4,
                    "topic" to 5,
                    "topic" to 6,
                )
            )
            listener.onPartitionsUnassigned(
                listOf(
                    "topic" to 4,
                    "topic" to 5,
                    "topic" to 6,
                    "topic" to 7,
                )
            )

        }

        @Test
        fun `onPartitionsUnassigned will not change the status if some partitions had not been unassigned`() {
            listener.onPartitionsAssigned(
                listOf(
                    "topic" to 4,
                    "topic" to 5,
                    "topic" to 6,
                )
            )
            listener.onPartitionsUnassigned(
                listOf(
                    "topic" to 4,
                    "topic" to 6,
                    "topic" to 7,
                )
            )
        }

        @Test
        fun `onPartitionsUnassigned call callback with the correct value`() {
            var currentPartitions: Set<Int>? = null
            listener.registerCallbackForTopic { partitions ->
                currentPartitions = partitions
            }
            listener.onPartitionsAssigned(
                listOf(
                    "topic" to 4,
                    "topic" to 5,
                    "topic" to 6,
                )
            )
            listener.onPartitionsUnassigned(
                listOf(
                    "topic" to 5,
                    "topic" to 7,
                )
            )

            assertThat(currentPartitions).containsExactly(4, 6)
        }
    }
    @Nested
    inner class OnPartitionsAssignedTests {
        @Test
        fun `onPartitionsAssigned will use only the correct topic`() {
            listener.onPartitionsAssigned(
                listOf(
                    "topic" to 4,
                    "anotherTopic" to 5,
                    "topic" to 6,
                    "topic" to 7,
                )
            )

            assertThat(listener.getCurrentlyAssignedPartitions()).containsExactly(4, 6, 7)
        }

        @Test
        fun `onPartitionsAssigned will update the state if we have partitions`() {
            listener.onPartitionsAssigned(
                listOf(
                    "topic" to 4,
                    "topic" to 6,
                    "topic" to 7,
                )
            )

        }

        @Test
        fun `onPartitionsAssigned will not update the state if we have no partitions`() {
            listener.onPartitionsAssigned(
                emptyList()
            )

        }

        @Test
        fun `onPartitionsAssigned will call the callback if the tile is running`() {
            var currentPartitions: Set<Int>? = null
            listener.registerCallbackForTopic { partitions ->
                currentPartitions = partitions
            }
            listener.onPartitionsAssigned(
                listOf(
                    "topic" to 4,
                    "topic" to 6,
                    "topic" to 4,
                )
            )

            assertThat(currentPartitions).containsExactly(4, 6)
        }
    }

    @Test
    fun `registerCallbackForTopic will call the callback if the tile is running`() {
        listener.onPartitionsAssigned(
            listOf(
                "topic" to 4,
                "topic" to 6,
                "topic" to 4,
            )
        )

        var called = 0
        listener.registerCallbackForTopic {
            called ++
        }

        assertThat(called).isOne
    }
}
