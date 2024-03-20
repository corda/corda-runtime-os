package net.corda.p2p.linkmanager.tracker

import com.fasterxml.jackson.annotation.JsonProperty
import com.fasterxml.jackson.core.JacksonException
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import net.corda.libs.statemanager.api.State
import net.corda.libs.statemanager.api.StateOperationGroup
import net.corda.messaging.api.records.EventLogRecord
import net.corda.v5.base.exceptions.CordaRuntimeException
import java.time.Instant
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong

internal class PartitionState(
    @JsonProperty("partition")
    private val partition: Int,
) {
    companion object {
        private val jsonParser = jacksonObjectMapper()
            .registerModule(JavaTimeModule())
        private const val STATE_PREFIX = "P2P-state-tracker"
        fun stateKey(
            partition: Int,
        ) = "$STATE_PREFIX-$partition"

        fun fromState(partition: Int, state: State?): PartitionState {
            return if (state != null) {
                try {
                    jsonParser.readValue<PartitionState>(state.value)
                } catch (e: JacksonException) {
                    throw CordaRuntimeException("Can not read state json.", e)
                }
            } else {
                PartitionState(partition)
            }
        }
    }

    @JsonProperty("messages")
    private val trackedMessages = ConcurrentHashMap<String, TrackedMessageState>()

    @JsonProperty("version")
    private val savedVersion = AtomicInteger(State.VERSION_INITIAL_VALUE)
    private val _restartOffset = AtomicLong(0)
    private val _lastSentOffset = AtomicLong(0)

    var restartOffset: Long
        get() { return _restartOffset.get() }
        set(l) { _restartOffset.set(l) }
    var lastSentOffset: Long
        get() { return _lastSentOffset.get() }
        set(l) { _lastSentOffset.set(l) }

    fun addMessage(message: TrackedMessageState) {
        trackedMessages[message.messageId] = message
    }

    fun untrackMessage(messageId: String) {
        trackedMessages.remove(messageId)
    }

    fun getTrackMessage(messageId: String): TrackedMessageState? = trackedMessages[messageId]

    fun addToOperationGroup(group: StateOperationGroup) {
        val value = jsonParser.writeValueAsBytes(this)
        val version = savedVersion.get()
        val key = stateKey(partition)
        val state = State(
            value = value,
            key = key,
            version = version,
        )
        if (version == State.VERSION_INITIAL_VALUE) {
            group.create(state)
        } else {
            group.update(state)
        }
    }

    fun read(
        now: Instant,
        records: List<EventLogRecord<String, *>>,
    ) {
        val offset = records.onEach { record ->
            trackedMessages[record.key] = TrackedMessageState(
                messageId = record.key,
                timeStamp = now,
                persisted = false,
            )
        }.maxOfOrNull {
            it.offset
        } ?: return
        if (offset > lastSentOffset) {
            lastSentOffset = offset
        }
    }
    fun sent(
        records: List<EventLogRecord<String, *>>,
    ) {
        val offset = records.maxOfOrNull {
            it.offset
        } ?: return
        if (offset > restartOffset) {
            restartOffset = offset
        }
    }

    fun saved() {
        savedVersion.incrementAndGet()
    }
}
