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

        fun getStates(records: Collection<RecordMetadata>): Collection<Pair<K, S?>> {
            return records.mapNotNull {
                val castRecord =
                    it.castToType(subscription.processor.keyClass, subscription.processor.stateValueClass)
                if (castRecord != null) {
                    val state = knownValues[castRecord.key]
                    if (state != null) {
                        castRecord.key to state.state
                    } else {
                        null
                    }
                } else {
                    null
                }
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
        val states = records.groupBy {
            it.partition
        }.map { (partitionId, records) ->
            knowPartitions[partitionId] to records
        }.filter { (partition, _) ->
            partition != null
        }
            .flatMap { (partition, records) ->
                if (partition!!.ready) {
                    partition.getStates(records)
                } else {
                    partition.applyNewData(records)
                    emptyList()
                }
            }.toMap()

        if ((subscription.stateAndEventListener != null) && (states.isNotEmpty())) {
            subscription.stateAndEventListener.onPostCommit(states)
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
}
