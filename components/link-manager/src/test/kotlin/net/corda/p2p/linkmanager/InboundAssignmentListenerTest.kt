package net.corda.p2p.linkmanager

import net.corda.lifecycle.LifecycleCoordinatorFactory
import net.corda.lifecycle.LifecycleCoordinatorName
import net.corda.lifecycle.domino.logic.ComplexDominoTile
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.mockito.Mockito
import org.mockito.kotlin.any
import org.mockito.kotlin.doAnswer
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

class InboundAssignmentListenerTest {

    companion object {
        const val TOPIC_1 = "topic"
        const val TOPIC_2 = "anotherTopic"
    }
    private val assign1 = listOf(1, 3, 5)
    private val assign2 = listOf(2, 3, 4)

    private val lifecycleCoordinatorFactory = mock<LifecycleCoordinatorFactory>()
    private val dominoTile = Mockito.mockConstruction(ComplexDominoTile::class.java) { mock, context ->
        @Suppress("UNCHECKED_CAST")
        whenever(mock.withLifecycleLock(any<() -> Any>())).doAnswer { (it.arguments.first() as () -> Any).invoke() }
        whenever(mock.isRunning).doReturn(true)
        @Suppress("UNCHECKED_CAST")
        whenever(mock.coordinatorName).doReturn(LifecycleCoordinatorName(context.arguments()[0] as String, ""))
    }

    @AfterEach
    fun cleanUp() {
        dominoTile.close()
    }

    @Test
    fun `Partitions can be assigned and reassigned`() {
        val listener = InboundAssignmentListener(lifecycleCoordinatorFactory)
        assertEquals(0, listener.getCurrentlyAssignedPartitions(TOPIC_1).size)
        val firstAssignment = assign1.map { TOPIC_1 to it } + assign2.map { TOPIC_2 to it }
        listener.onPartitionsAssigned(firstAssignment)
        assertArrayEquals(assign1.toIntArray(), listener.getCurrentlyAssignedPartitions(TOPIC_1).toIntArray())
        assertArrayEquals(assign2.toIntArray(), listener.getCurrentlyAssignedPartitions(TOPIC_2).toIntArray())
        val unAssignTopic = 1
        listener.onPartitionsUnassigned(listOf(TOPIC_1 to unAssignTopic))
        assertArrayEquals((assign1 - listOf(unAssignTopic)).toIntArray(), listener.getCurrentlyAssignedPartitions(TOPIC_1).toIntArray())
    }

    @Test
    fun `resource started is called when topic has partitions`() {
        val listener = InboundAssignmentListener(lifecycleCoordinatorFactory)
        val assignment = assign1.map { TOPIC_1 to it } + assign2.map { TOPIC_2 to it }
        listener.onPartitionsAssigned(assignment)

        verify(dominoTile.constructed().first()).resourcesStarted()
    }

    @Test
    fun `the callback is called on assignment if registered before the first assignment`() {
        val topic1CallbackArguments = mutableListOf<Set<Int>>()
        val topic2CallbackArguments = mutableListOf<Set<Int>>()
        val listener = InboundAssignmentListener(lifecycleCoordinatorFactory)
        listener.registerCallbackForTopic(TOPIC_1) { partitions -> topic1CallbackArguments.add(partitions) }
        listener.registerCallbackForTopic(TOPIC_2) { partitions -> topic2CallbackArguments.add(partitions) }
        assertThat(topic1CallbackArguments).isEmpty()
        val firstAssignment = assign1.map { TOPIC_1 to it } + assign2.map { TOPIC_2 to it }
        listener.onPartitionsAssigned(firstAssignment)
        assertThat(topic1CallbackArguments.single()).containsExactlyInAnyOrderElementsOf(assign1)
        assertThat(topic2CallbackArguments.single()).containsExactlyInAnyOrderElementsOf(assign2)
    }

    @Test
    fun `the callback is called on registration if registered after the first assignment`() {
        val callbackArguments = mutableListOf<Set<Int>>()
        val listener = InboundAssignmentListener(lifecycleCoordinatorFactory)
        val assign1 = listOf(1, 3, 5)
        val firstAssignment = assign1.map { TOPIC_1 to it } + assign2.map { TOPIC_2 to it }
        listener.onPartitionsAssigned(firstAssignment)
        listener.registerCallbackForTopic(TOPIC_1) { partitions -> callbackArguments.add(partitions) }
        assertThat(callbackArguments.single()).containsExactlyInAnyOrderElementsOf(assign1)
    }

    @Test
    fun `the callback is called when partitions are unassigned`() {
        val callbackArguments = mutableListOf<Set<Int>>()
        val listener = InboundAssignmentListener(lifecycleCoordinatorFactory)
        assertEquals(0, listener.getCurrentlyAssignedPartitions(TOPIC_1).size)
        val firstAssignment = assign1.map { TOPIC_1 to it } + assign2.map { TOPIC_2 to it }
        listener.onPartitionsAssigned(firstAssignment)
        listener.registerCallbackForTopic(TOPIC_1) { partitions -> callbackArguments.add(partitions) }
        val unAssignTopic = 1
        listener.onPartitionsUnassigned(listOf(TOPIC_1 to unAssignTopic))
        assertThat(callbackArguments.last()).containsExactlyInAnyOrderElementsOf(assign1 - listOf(unAssignTopic))
    }
}