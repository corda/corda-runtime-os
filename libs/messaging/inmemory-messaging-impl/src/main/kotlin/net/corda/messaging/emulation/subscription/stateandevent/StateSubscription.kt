package net.corda.messaging.emulation.subscription.stateandevent

import net.corda.lifecycle.Lifecycle
import net.corda.messaging.api.subscription.PartitionAssignmentListener
import net.corda.messaging.emulation.topic.model.Consumption
import net.corda.messaging.emulation.topic.model.RecordMetadata
import java.util.concurrent.ConcurrentHashMap

internal class StateSubscription<K : Any, S : Any>(
    internal val subscription: InMemoryStateAndEventSubscription<K, S, *>,
) : Lifecycle, PartitionAssignmentListener {

    private data class State<S : Any>(val state: S?)

    private var stateConsumption: Consumption? = null
    override val isRunning: Boolean
        get() =
            stateConsumption?.isRunning ?: false

    override fun start() {
        if (stateConsumption == null) {
            val consumer = StatesConsumer(this)
            stateConsumption = subscription.topicService.subscribe(consumer)
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
            if (!ready) {
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

    private fun waitingForData(): Boolean {
        return knowPartitions.values.any { !it.ready }
    }

    fun gotStates(records: Collection<RecordMetadata>) {
        records.groupBy {
            it.partition
        }.forEach { (partitionId, records) ->
            knowPartitions[partitionId]?.applyNewData(records)
        }

        if (!waitingForData()) {
            subscription.stateAndEventListener?.onPostCommit(createFullDataMap())
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

    private fun createFullDataMap(): Map<K, S?> {
        return knowPartitions.values.map {
            it.knownValues
        }.flatMap {
            it.entries
        }.associate {
            it.key to it.value.state
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
}
