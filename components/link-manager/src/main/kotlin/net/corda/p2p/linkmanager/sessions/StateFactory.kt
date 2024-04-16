package net.corda.p2p.linkmanager.sessions

import net.corda.libs.statemanager.api.Metadata
import net.corda.libs.statemanager.api.State
import net.corda.p2p.linkmanager.state.SessionState

internal class StateFactory(
    private val stateConvertor: StateConvertor,
) {
    fun createState(state: State, metadata: Metadata) = state.copy(
        metadata = metadata
    )

    fun createState(key: String, sessionState: SessionState, version: Int = 0, metadata: Metadata) = State(
        key = key,
        value = stateConvertor.toStateByteArray(sessionState),
        version = version,
        metadata = metadata,
    )
}