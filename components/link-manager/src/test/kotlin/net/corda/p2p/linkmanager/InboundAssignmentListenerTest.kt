package net.corda.p2p.linkmanager

import net.corda.lifecycle.LifecycleCoordinatorFactory
import net.corda.lifecycle.domino.logic.DominoTileState
import net.corda.lifecycle.domino.logic.SimpleDominoTile
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.mockito.Mockito.mockConstruction
import org.mockito.kotlin.any
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

class InboundAssignmentListenerTest {

    private val lifecycleCoordinatorFactory = mock<LifecycleCoordinatorFactory>()
    private val tile = mockConstruction(SimpleDominoTile::class.java) { _, _ ->
    }
    private val listener = InboundAssignmentListener(lifecycleCoordinatorFactory, "topic")

    @AfterEach
    fun cleanUp() {
        tile.close()
    }

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

            verify(tile.constructed().first()).updateState(DominoTileState.StoppedDueToBadConfig)
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

            verify(tile.constructed().first(), never()).updateState(DominoTileState.StoppedDueToBadConfig)
        }

        @Test
        fun `onPartitionsUnassigned call callback with the correct value`() {
            whenever(tile.constructed().first().isRunning).doReturn(true)
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

            verify(tile.constructed().first()).updateState(DominoTileState.Started)
        }

        @Test
        fun `onPartitionsAssigned will not update the state if we have no partitions`() {
            listener.onPartitionsAssigned(
                emptyList()
            )

            verify(tile.constructed().first(), never()).updateState(any())
        }

        @Test
        fun `onPartitionsAssigned will call the callback if the tile is running`() {
            whenever(tile.constructed().first().isRunning).doReturn(true)
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

        @Test
        fun `onPartitionsAssigned will not call the callback if the tile is not running`() {
            whenever(tile.constructed().first().isRunning).doReturn(false)
            var called = 0
            listener.registerCallbackForTopic {
                called ++
            }
            listener.onPartitionsAssigned(
                listOf(
                    "topic" to 4,
                    "topic" to 6,
                    "topic" to 4,
                )
            )

            assertThat(called).isZero
        }
    }

    @Test
    fun `registerCallbackForTopic will call the callback if the tile is running`() {
        whenever(tile.constructed().first().isRunning).doReturn(true)
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

    @Test
    fun `registerCallbackForTopic will not call the callback if the tile is not running`() {
        whenever(tile.constructed().first().isRunning).doReturn(false)
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

        assertThat(called).isZero
    }
}
