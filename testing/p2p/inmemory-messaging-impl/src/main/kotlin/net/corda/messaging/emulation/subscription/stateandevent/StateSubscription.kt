package net.corda.messaging.emulation.subscription.stateandevent

import net.corda.lifecycle.Lifecycle
import net.corda.messaging.api.subscription.listener.PartitionAssignmentListener
import net.corda.messaging.emulation.topic.model.Consumption
import net.corda.messaging.emulation.topic.model.RecordMetadata
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import java.util.concurrent.locks.Lock
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

internal class StateSubscription<K : Any, S : Any>(
    internal val subscription: InMemoryStateAndEventSubscription<K, S, *>,
    private val waitForReadyLock: Lock = ReentrantLock()
) : Lifecycle, PartitionAssignmentListener {

    private data class State<S : Any>(val state: S?)

    private val readyNotifier = waitForReadyLock.newCondition()

    internal val consumer = StatesConsumer(this)
    private var stateConsumption: Consumption? = null
    override val isRunning: Boolean
        get() =
            stateConsumption?.isRunning ?: false

    override fun start() {
        if (stateConsumption == null) {
            stateConsumption = subscription.topicService.createConsumption(consumer)
        }
    }

    override fun stop() {
        stateConsumption?.stop()
        stateConsumption = null
    }

    private inner class PartitionData(id: Int) {
        private val latestOffsetOnCreation =
            subscription.topicService
                .getLatestOffsets(
                    subscription.stateSubscriptionConfig.eventTopic
                )[id] ?: -1
        val knownValues = ConcurrentHashMap<K, State<S>>()
        @Volatile
        var ready = false

        init {
            if (latestOffsetOnCreation < 0L) {
                reportSynch()
            }
        }

        fun applyNewData(records: Collection<RecordMetadata>) {
            records.onEach {
                val castRecord = it.castToType(subscription.processor.keyClass, subscription.processor.stateValueClass)
                if (castRecord != null) {
                    knownValues[castRecord.key] = State(castRecord.value)
                }
                if (it.offset == latestOffsetOnCreation) {
                    reportSynch()
                }
            }
        }

        fun reportLost() {
            subscription.stateAndEventListener?.onPartitionLost(
                createReportMap()
            )
        }
        fun reportSynch() {
            subscription.stateAndEventListener?.onPartitionSynced(
                createReportMap()
            )
            ready = true
            waitForReadyLock.withLock {
                readyNotifier.signalAll()
            }
        }
        fun createReportMap(): Map<K, S> {
            return knownValues
                .filterValues {
                    it.state != null
                }.mapValues {
                    it.value.state!!
                }
        }
    }

    private val knowPartitions = ConcurrentHashMap<Int, PartitionData>()

    fun gotStates(records: Collection<RecordMetadata>) {
        records.groupBy {
            it.partition
        }.map { (partitionId, records) ->
            knowPartitions[partitionId] to records
        }.filter { (partition, _) ->
            partition?.ready == false
        }.onEach { (partition, records) ->
            partition?.applyNewData(records)
        }
    }

    override fun onPartitionsUnassigned(topicPartitions: List<Pair<String, Int>>) {
        topicPartitions.forEach { (_, partitionId) ->
            knowPartitions.remove(partitionId)?.reportLost()
        }
    }

    override fun onPartitionsAssigned(topicPartitions: List<Pair<String, Int>>) {
        topicPartitions.forEach { (_, partitionId) ->
            knowPartitions.computeIfAbsent(partitionId) {
                PartitionData(it)
            }
        }
    }

    fun getValue(key: K): S? {
        return knowPartitions.values.mapNotNull {
            it.knownValues[key]
        }.mapNotNull {
            it.state
        }
            .firstOrNull()
    }

    fun setValue(key: K, updatedState: S?, partition: Int) {
        knowPartitions[partition]?.knownValues?.put(key, State(updatedState))
    }

    fun waitForReady() {
        while (isRunning) {
            if (knowPartitions.values.all { it.ready }) {
                return
            }
            waitForReadyLock.withLock {
                readyNotifier.await(10, TimeUnit.SECONDS)
            }
        }
    }
}
