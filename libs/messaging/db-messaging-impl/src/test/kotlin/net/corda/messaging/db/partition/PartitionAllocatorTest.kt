package net.corda.messaging.db.partition

import net.corda.messaging.db.persistence.DBAccessProvider
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.within
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Mockito.`when`
import org.mockito.Mockito.mock

class PartitionAllocatorTest {

    private val numberOfPartitions = 10
    private val partitions = (1..numberOfPartitions).toList()

    private val topic1 = "test.topic1"
    private val topic2 = "test.topic2"

    private val partitions1 = listOf(topic1, topic2).map { it to mutableSetOf<Int>() }.toMap()
    private val partitions2 = listOf(topic1, topic2).map { it to mutableSetOf<Int>() }.toMap()
    private val partitions3 = listOf(topic1, topic2).map { it to mutableSetOf<Int>() }.toMap()
    private val partitions4 = listOf(topic1, topic2).map { it to mutableSetOf<Int>() }.toMap()

    private val listener1 = InMemoryListener(partitions1)
    private val listener2 = InMemoryListener(partitions2)
    private val listener3 = InMemoryListener(partitions3)
    private val listener4 = InMemoryListener(partitions4)

    private val dbAccessProvider = mock(DBAccessProvider::class.java).apply {
        `when`(getTopics()).thenReturn(mapOf(
            topic1 to numberOfPartitions,
            topic2 to numberOfPartitions
        ))
    }

    private val partitionAllocator = PartitionAllocator(dbAccessProvider)

    @BeforeEach
    fun setup() {
        partitionAllocator.start()
    }

    @Test
    fun `partitions are allocated equally among those registered`() {
        partitionAllocator.register(topic1, listener1)
        assertThat(listener1.allocatedPartitions[topic1]!!).containsExactlyInAnyOrderElementsOf(partitions)
        assertThat(listener2.allocatedPartitions[topic1]!!).isEmpty()
        assertThat(listener3.allocatedPartitions[topic1]!!).isEmpty()
        assertThat(listener4.allocatedPartitions[topic1]!!).isEmpty()

        partitionAllocator.register(topic1, listener2)
        assertThat(listener1.allocatedPartitions[topic1]!!).containsExactlyInAnyOrderElementsOf(setOf(1, 2, 3, 4, 5))
        assertThat(listener2.allocatedPartitions[topic1]!!).containsExactlyInAnyOrderElementsOf(setOf(6, 7, 8, 9, 10))
        assertThat(listener3.allocatedPartitions[topic1]!!).isEmpty()
        assertThat(listener4.allocatedPartitions[topic1]!!).isEmpty()

        partitionAllocator.register(topic1, listener3)
        assertThat(listener1.allocatedPartitions[topic1]!!).containsExactlyInAnyOrderElementsOf(setOf(1, 2, 3, 4))
        assertThat(listener2.allocatedPartitions[topic1]!!).containsExactlyInAnyOrderElementsOf(setOf(5, 6, 7))
        assertThat(listener3.allocatedPartitions[topic1]!!).containsExactlyInAnyOrderElementsOf(setOf(8, 9, 10))
        assertThat(listener4.allocatedPartitions[topic1]!!).isEmpty()

        partitionAllocator.register(topic1, listener4)
        assertThat(listener1.allocatedPartitions[topic1]!!).containsExactlyInAnyOrderElementsOf(setOf(1, 2, 3))
        assertThat(listener2.allocatedPartitions[topic1]!!).containsExactlyInAnyOrderElementsOf(setOf(4, 5, 6))
        assertThat(listener3.allocatedPartitions[topic1]!!).containsExactlyInAnyOrderElementsOf(setOf(7, 8))
        assertThat(listener4.allocatedPartitions[topic1]!!).containsExactlyInAnyOrderElementsOf(setOf(9, 10))
    }

    @Test
    fun `allocation sizes do not differ by more than one and they contain contiguous partitions`() {
        `when`(dbAccessProvider.getTopics()).thenReturn(mapOf(
            topic1 to 50
        ))

        for (numberOfRegistrations in 1..10) {
            val partitionAllocator = PartitionAllocator(dbAccessProvider)
            partitionAllocator.start()

            val registeredListeners = (1..numberOfRegistrations).map { InMemoryListener(mutableMapOf(topic1 to mutableSetOf())) }
            registeredListeners.forEach { partitionAllocator.register(topic1, it) }

            // check allocation contains contiguous numbers
            registeredListeners.forEach { listener ->
                val min = listener.allocatedPartitions[topic1]!!.minOrNull()!!
                val max = listener.allocatedPartitions[topic1]!!.maxOrNull()!!
                val range = (min..max).toSet()
                assertThat(listener.allocatedPartitions[topic1]!!).containsExactlyInAnyOrderElementsOf(range)
            }
            // check allocation sizes do not differ by more than one
            registeredListeners.forEach { listener ->
                assertThat(listener.allocatedPartitions[topic1]!!.size).isCloseTo(registeredListeners[0].allocatedPartitions[topic1]!!.size, within(1))
            }
        }
    }

    @Test
    fun `allocations to one topic do not interfere with allocations on a separate topic`() {
        partitionAllocator.register(topic1, listener1)
        partitionAllocator.register(topic2, listener2)

        assertThat(listener1.allocatedPartitions[topic1]!!).isNotEmpty
        assertThat(listener1.allocatedPartitions[topic2]!!).isEmpty()
        assertThat(listener2.allocatedPartitions[topic1]!!).isEmpty()
        assertThat(listener2.allocatedPartitions[topic2]!!).isNotEmpty
    }

    class InMemoryListener(val allocatedPartitions: Map<String, MutableSet<Int>>): PartitionAllocationListener {
        override fun onPartitionsAssigned(topic: String, partitions: Set<Int>) {
            allocatedPartitions[topic]!!.addAll(partitions)
        }

        override fun onPartitionsUnassigned(topic: String, partitions: Set<Int>) {
            allocatedPartitions[topic]!!.removeAll(partitions)
        }

    }

}