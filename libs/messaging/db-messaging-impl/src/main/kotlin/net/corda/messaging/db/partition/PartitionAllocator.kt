package net.corda.messaging.db.partition

import net.corda.lifecycle.LifeCycle
import net.corda.messaging.api.subscription.Subscription
import net.corda.messaging.db.persistence.DBAccessProvider
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock
import kotlin.math.ceil

class PartitionAllocator(private val dbAccessProvider: DBAccessProvider): LifeCycle {

    @Volatile
    private var running = false
    private val startStopLock = ReentrantLock()

    private lateinit var partitionsPerTopic: Map<String, Int>
    private val registeredListenersPerTopic: MutableMap<String, MutableList<PartitionAllocationListener>> = mutableMapOf()
    private val currentAllocationsPerTopic: MutableMap<String, MutableMap<PartitionAllocationListener, List<Int>>> = mutableMapOf()

    override val isRunning: Boolean
        get() = running

    override fun start() {
        startStopLock.withLock {
            if (!running) {
                partitionsPerTopic = dbAccessProvider.getTopics()
                partitionsPerTopic.keys.forEach { topic ->
                    registeredListenersPerTopic[topic] = mutableListOf()
                    currentAllocationsPerTopic[topic] = mutableMapOf()
                }

                running = true
            }
        }
    }

    override fun stop() {
        startStopLock.withLock {
            if (running) {
                running = false
            }
        }
    }

    /**
     * This method is supposed to be called by [Subscription]s once they start.
     *
     * Every time a new subscription is registered, the available partitions are re-balanced across the already registered subscriptions.
     * The listeners of the subscriptions that have been registered are invoked to inform them about assigned/unassigned partitions.
     *
     * Only one registration (and corresponding re-balancing) can be happening at a time per topic.
     *
     * @param topic the topic the subscription registers for.
     * @param partitionAllocationListener the listener to be invoked once partitions are allocated.
     */
    fun register(topic: String, partitionAllocationListener: PartitionAllocationListener) {
        synchronized(registeredListenersPerTopic[topic]!!) {
            registeredListenersPerTopic[topic]!!.add(partitionAllocationListener)
            reallocatePartitions(topic)
        }
    }

    /**
     * Splits the partitions of the specified topic amongst the available listeners,
     * so that allocation sizes do not differ by more than one and partitions are allocated sequentially.
     */
    private fun reallocatePartitions(topic: String) {
        val registeredListeners = registeredListenersPerTopic[topic]!!
        val partitions = (1..partitionsPerTopic[topic]!!).toMutableList()
        var remainingListeners = registeredListeners.size

        registeredListeners.forEach { listener ->
            val partitionsPerListener = ceil(partitions.size.toDouble() / remainingListeners.toDouble()).toInt()
            val newAllocation = mutableListOf<Int>()
            for (i in 1..partitionsPerListener) {
                newAllocation.add(partitions.removeFirst())
            }
            remainingListeners--

            val previousAllocation = currentAllocationsPerTopic[topic]!![listener] ?: emptyList()
            currentAllocationsPerTopic[topic]!![listener] = newAllocation

            val unassignedPartitions = previousAllocation - newAllocation
            val assignedPartitions = newAllocation - previousAllocation

            listener.onPartitionsUnassigned(topic, unassignedPartitions.toSet())
            listener.onPartitionsAssigned(topic, assignedPartitions.toSet())
        }
    }

}