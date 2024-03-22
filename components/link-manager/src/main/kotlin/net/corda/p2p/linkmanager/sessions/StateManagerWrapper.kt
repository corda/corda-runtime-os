package net.corda.p2p.linkmanager.sessions

import net.corda.libs.statemanager.api.MetadataFilter
import net.corda.libs.statemanager.api.State
import net.corda.libs.statemanager.api.StateManager
import net.corda.metrics.CordaMetrics
import net.corda.p2p.crypto.protocol.api.CheckRevocation
import net.corda.p2p.linkmanager.metrics.recordP2PMetric
import net.corda.p2p.linkmanager.metrics.recordSessionCreationTime
import net.corda.p2p.linkmanager.sessions.metadata.OutboundSessionMetadata.Companion.isOutbound
import net.corda.p2p.linkmanager.sessions.metadata.OutboundSessionMetadata.Companion.toOutbound
import net.corda.p2p.linkmanager.sessions.metadata.OutboundSessionStatus
import net.corda.p2p.linkmanager.sessions.metadata.toCounterparties
import net.corda.p2p.linkmanager.state.SessionState
import net.corda.p2p.linkmanager.state.direction
import org.slf4j.Logger
import org.slf4j.LoggerFactory

internal class StateManagerWrapper(
    private val stateManager: StateManager,
    private val sessionCache: SessionCache,
    private val stateConvertor: StateConvertor,
    private val checkRevocation: CheckRevocation,
    private val reEstablishmentMessageSender: ReEstablishmentMessageSender,
) {
    private companion object {
        val logger: Logger = LoggerFactory.getLogger(StateManagerWrapper::class.java)
    }

    data class StateManagerSessionState(
        val managerState: State,
        val sessionState: SessionState,
    ) {
        fun toCounterparties() = managerState.toCounterparties()
    }
    fun get(
        keys: Collection<String>,
    ) = sessionCache.validateStatesAndScheduleExpiry(
        stateManager.get(keys),
    ).toStates()

    fun findStatesMatchingAny(
        filters: Collection<MetadataFilter>,
    ) = sessionCache.validateStatesAndScheduleExpiry(
        stateManager.findByMetadataMatchingAny(filters),
    ).toStates()

    fun upsert(
        changes: Collection<StateManagerAction>,
    ): Map<String, State?> {
        val updateActions = changes.filterIsInstance<UpdateAction>()
        val updates = updateActions
            .map {
                it.state
            }.mapNotNull {
                sessionCache.validateStateAndScheduleExpiry(
                    state = it,
                    beforeUpdate = true,
                )
            }
        val creates = changes.filterIsInstance<CreateAction>()
            .map {
                it.state
            }.mapNotNull {
                sessionCache.validateStateAndScheduleExpiry(it)
            }
        val failedUpdates = if (updates.isNotEmpty()) {
            stateManager.update(updates).onEach {
                logger.info("Failed to update the state of session with ID ${it.key}")
            }
        } else {
            emptyMap()
        }
        val completedUpdates = (updateActions.associateBy { it.state.key } - failedUpdates.keys).values
        recordSessionReplayMetrics(completedUpdates)
        recordSessionEstablishmentMetrics(completedUpdates.map { it.state })
        val failedCreates = if (creates.isNotEmpty()) {
            stateManager.create(creates).associateWith {
                logger.info("Failed to create the state of session with ID $it")
                null
            }
        } else {
            emptyMap()
        }
        recordSessionStartMetrics((creates.associateBy { it.key } - failedCreates.keys).values)
        return failedUpdates + failedCreates
    }

    private fun recordSessionStartMetrics(creates: Collection<State>) {
        creates.groupBy { it.direction() }.forEach {
            recordP2PMetric(CordaMetrics.Metric.SessionStartedCount, it.key, it.value.size.toDouble())
        }
    }

    private fun recordSessionEstablishmentMetrics(updates: Collection<State>) {
        val allEstablished = updates.filter { it.metadata.isOutbound() }
            .map { it.metadata.toOutbound() }
            .filter { it.status == OutboundSessionStatus.SessionReady }
        CordaMetrics.Metric.SessionEstablishedCount.builder().build().increment(allEstablished.size.toDouble())
        allEstablished.forEach { recordSessionCreationTime(it.initiationTimestamp) }
    }

    private fun recordSessionReplayMetrics(updates: Collection<UpdateAction>) {
        updates.filter { it.isReplay }.groupBy { it.state.direction() }.forEach {
            recordP2PMetric(CordaMetrics.Metric.SessionMessageReplayCount, it.key, it.value.size.toDouble())
        }
    }
    private fun Map<String, State>.toStates():  Map<String, StateManagerSessionState> {
        return this.mapNotNull { (key, state) ->
            val session = stateConvertor.toCordaSessionState(
                state,
                checkRevocation,
            )
            if (session == null) {
                sessionCache.forgetState(state)
                if (!state.metadata.isOutbound()) {
                    reEstablishmentMessageSender.send(state)
                }
                null
            } else {
                key to StateManagerSessionState(state, session)
            }
        }.toMap()
    }
}
