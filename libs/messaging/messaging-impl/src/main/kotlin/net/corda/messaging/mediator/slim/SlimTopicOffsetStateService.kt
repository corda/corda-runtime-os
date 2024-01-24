package net.corda.messaging.mediator.slim

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import net.corda.libs.statemanager.api.State
import net.corda.libs.statemanager.api.StateManager
import net.corda.messagebus.api.CordaTopicPartition
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

class SlimTopicOffsetStateService(
    private val stateManager: StateManager,
) {
    private val mapper = ObjectMapper().registerKotlinModule()
    private val currentOffsets = mutableMapOf<String, OffsetState>()
    private val lock = ReentrantLock()

    fun getOffsetsOrDefaultFor(topic: String, partitions: List<Int>): List<TopicOffset> {
        lock.withLock {
            return partitions.map {
                val state = stateManager.get(listOf("$topic-$it")).map { it.value }.firstOrNull()
                val key = getKey(topic, it)
                if (state == null) {
                    val defaultValue = TopicOffset(CordaTopicPartition(topic, it), 0)
                    currentOffsets[key] =
                        OffsetState(State(key, mapper.writeValueAsBytes(defaultValue)), defaultValue)
                    defaultValue
                } else {
                    val currentOffset = mapper.readValue(state.value, TopicOffset::class.java)
                    currentOffsets[key] = OffsetState(state, currentOffset)
                    currentOffset
                }
            }
        }
    }

    fun getUpdatedStates(offsets: List<TopicOffset>): List<State> {
        return lock.withLock {
            offsets.map {
                val key = getKey(it.topicPartition.topic, it.topicPartition.partition)
                val offsetState = checkNotNull(currentOffsets[key])
                val newState = offsetState.state.copy(
                    value = mapper.writeValueAsBytes(it),
                    version = offsetState.state.version + 1,
                )
                currentOffsets[key] = OffsetState(newState, it)
                newState
            }
        }
    }

    private fun getKey(topic: String, partition: Int): String {
        return "$topic-$partition"
    }

    private data class OffsetState(val state: State, val topicOffset: TopicOffset)
}
