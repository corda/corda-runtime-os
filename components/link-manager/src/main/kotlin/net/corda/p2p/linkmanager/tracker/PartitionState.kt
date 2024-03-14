package net.corda.p2p.linkmanager.tracker

import com.fasterxml.jackson.core.JsonParseException
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
    val partition: Int,
    state: State?,
) {
    companion object {
        private val jsonParser = jacksonObjectMapper()
        private const val STATE_PREFIX = "P2P-state-tracker"
        fun stateKey(
            partition: Int,
        ) = "$STATE_PREFIX-$partition"
    }
    private val trackedMessages = ConcurrentHashMap<String, TrackedMessageState>()
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

    init {
        if (state != null) {
            savedVersion.set(state.version)
            val savedData = try {
                jsonParser.readValue<Map<String, Any?>>(state.value)
            } catch (e: JsonParseException) {
                throw CordaRuntimeException("Can not read state json.", e)
            }
            restartOffset = (savedData["restartOffset"] as? Number)?.toLong()
                ?: throw CordaRuntimeException("invalid restartOffset")
            lastSentOffset = (savedData["lastSentOffset"] as? Number)?.toLong()
                ?: throw CordaRuntimeException("invalid restartOffset")
            val rawTrackedMessages = (savedData["trackedMessages"] as? Collection<*>)
                ?: throw CordaRuntimeException("invalid trackedMessages")
            rawTrackedMessages.map {
                it as? Map<*, *> ?: throw CordaRuntimeException("invalid trackedMessages")
            }.map {
                val messageId = it["id"] as? String ?: throw CordaRuntimeException("invalid trackedMessages id")
                val timeStamp =
                    (it["ts"] as? Number)?.toLong() ?: throw CordaRuntimeException("invalid trackedMessages ts")
                val persisted = it["p"] as? Boolean ?: throw CordaRuntimeException("invalid trackedMessages p")
                TrackedMessageState(
                    messageId = messageId,
                    timeStamp = Instant.ofEpochMilli(timeStamp),
                    persisted = persisted,
                )
            }.forEach {
                addMessage(it)
            }
        }
    }

    fun addToOperationGroup(group: StateOperationGroup) {
        val data = mapOf(
            "restartOffset" to restartOffset,
            "lastSentOffset" to lastSentOffset,
            "trackedMessages" to trackedMessages.values.map {
                mapOf(
                    "id" to it.messageId,
                    "ts" to it.timeStamp.toEpochMilli(),
                    "p" to it.persisted,
                )
            },
        )
        val value = jsonParser.writeValueAsBytes(data)
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
