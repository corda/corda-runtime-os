package net.corda.p2p.linkmanager.tracker

import com.fasterxml.jackson.annotation.JsonIgnore
import com.fasterxml.jackson.annotation.JsonProperty
import com.fasterxml.jackson.core.JacksonException
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import net.corda.data.p2p.app.AuthenticatedMessage
import net.corda.libs.statemanager.api.State
import net.corda.libs.statemanager.api.StateOperationGroup
import net.corda.p2p.linkmanager.sessions.SessionManager
import net.corda.v5.base.exceptions.CordaRuntimeException
import net.corda.v5.base.types.MemberX500Name
import net.corda.virtualnode.HoldingIdentity
import java.time.Instant
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong

internal class PartitionState(
    @JsonProperty("partition")
    private val partition: Int,
    @JsonIgnore
    private val toCreate: AtomicBoolean = AtomicBoolean(false)
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
                PartitionState(
                    partition,
                    AtomicBoolean(true)
                )
            }
        }
    }

    @JsonProperty("messages")
    private val trackedMessages =
        GroupToMessages()

    @JsonProperty("version")
    private val savedVersion = AtomicInteger(State.VERSION_INITIAL_VALUE)
    private val _readRecordsFromOffset = AtomicLong(-1)
    private val _processRecordsFromOffset = AtomicLong(-1)

    var readRecordsFromOffset: Long
        get() { return _readRecordsFromOffset.get() }
        set(l) { _readRecordsFromOffset.set(l) }
    var processRecordsFromOffset: Long
        get() { return _processRecordsFromOffset.get() }
        set(l) { _processRecordsFromOffset.set(l) }

    fun counterpartiesToMessages(): Collection<Pair<SessionManager.Counterparties, Collection<TrackedMessageState>>> {
        return trackedMessages.flatMap { (groupId, ourNameToMessages) ->
            ourNameToMessages.flatMap { (ourName, theirNameToMessages) ->
                val ourId = HoldingIdentity(
                    MemberX500Name.parse(ourName),
                    groupId,
                )
                theirNameToMessages.map { (theirName, messages) ->
                    val theirId = HoldingIdentity(
                        MemberX500Name.parse(theirName),
                        groupId,
                    )
                    val counterparties = SessionManager.Counterparties(
                        ourId = ourId,
                        counterpartyId = theirId,
                    )
                    counterparties to messages.values
                }
            }
        }
    }

    fun addToOperationGroup(group: StateOperationGroup) {
        val value = jsonParser.writeValueAsBytes(this)
        val version = savedVersion.get()
        val key = stateKey(partition)
        val state = State(
            value = value,
            key = key,
            version = version,
        )
        if (toCreate.get()) {
            group.create(state)
        } else {
            group.update(state)
        }
    }

    fun read(
        now: Instant,
        records: Collection<MessageRecord>,
    ): Collection<MessageRecord> {
        return records.filter { record ->
            val id = record.message.header.messageId
            trackedMessages.getOrPut(record.message.header.source.groupId) {
                OurNameToMessages()
            }.getOrPut(record.message.header.source.x500Name) {
                TheirNameToMessages()
            }.getOrPut(record.message.header.destination.x500Name) {
                MessageIdToMessage()
            }.putIfAbsent(
                id,
                TrackedMessageState(
                    messageId = id,
                    timeStamp = now,
                ),
            ) == null
        }
    }

    fun saved() {
        if (!toCreate.getAndSet(false)) {
            savedVersion.incrementAndGet()
        }
    }

    fun trim() {
        trackedMessages.entries.removeIf { (_, group) ->
            group.entries.removeIf { (_, source) ->
                source.entries.removeIf { (_, destination) ->
                    destination.isEmpty()
                }
                source.isEmpty()
            }
            group.isEmpty()
        }
    }

    fun forget(message: AuthenticatedMessage) {
        trackedMessages[message.header.source.groupId]
            ?.get(message.header.source.x500Name)
            ?.get(message.header.destination.x500Name)
            ?.remove(message.header.messageId)
    }
}
private typealias MessageIdToMessage = ConcurrentHashMap<String, TrackedMessageState>
private typealias TheirNameToMessages = ConcurrentHashMap<String, MessageIdToMessage>
private typealias OurNameToMessages = ConcurrentHashMap<String, TheirNameToMessages>
private typealias GroupToMessages = ConcurrentHashMap<String, OurNameToMessages>
