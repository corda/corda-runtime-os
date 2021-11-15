package net.corda.p2p.linkmanager

import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import java.util.concurrent.CompletableFuture
import java.util.concurrent.atomic.AtomicReference

class InboundAssignmentListenerTest {

    companion object {
        const val TOPIC_1 = "topic"
        const val TOPIC_2 = "anotherTopic"
    }

    @Test
    fun `Partitions can be assigned and reassigned`() {
        val reference = AtomicReference<CompletableFuture<Unit>>()
        reference.set(mock())
        val listener = InboundAssignmentListener(reference)
        assertEquals(0, listener.getCurrentlyAssignedPartitions(TOPIC_1).size)
        val assign1 = listOf(1, 3, 5)
        val assign2 = listOf(2, 3, 4)
        val firstAssignment = assign1.map { TOPIC_1 to it } + assign2.map { TOPIC_2 to it }
        listener.onPartitionsAssigned(firstAssignment)
        assertArrayEquals(assign1.toIntArray(), listener.getCurrentlyAssignedPartitions(TOPIC_1).toIntArray())
        assertArrayEquals(assign2.toIntArray(), listener.getCurrentlyAssignedPartitions(TOPIC_2).toIntArray())
        val unAssignTopic = 1
        listener.onPartitionsUnassigned(listOf(TOPIC_1 to unAssignTopic))
        assertArrayEquals((assign1 - listOf(unAssignTopic)).toIntArray(), listener.getCurrentlyAssignedPartitions(TOPIC_1).toIntArray())
    }

    @Test
    fun `the future completes when partitions are assigned`() {
        val future = mock<CompletableFuture<Unit>>()
        val reference = AtomicReference<CompletableFuture<Unit>>()
        reference.set(future)
        val listener = InboundAssignmentListener(reference)
        assertEquals(0, listener.getCurrentlyAssignedPartitions(TOPIC_1).size)
        val assign1 = listOf(1, 3, 5)
        val assign2 = listOf(2, 3, 4)
        val firstAssignment = assign1.map { TOPIC_1 to it } + assign2.map { TOPIC_2 to it }
        listener.onPartitionsAssigned(firstAssignment)
        val secondAssignment = assign2.map { TOPIC_1 to it } + assign1.map { TOPIC_2 to it }
        listener.onPartitionsAssigned(secondAssignment)
        verify(future).complete(Unit)
    }
}