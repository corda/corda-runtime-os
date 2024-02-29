package net.corda.p2p.linkmanager.sessions

import net.corda.libs.statemanager.api.State

internal interface StateManagerAction {
    val state: State
}

internal data class CreateAction(
    override val state: State,
) : StateManagerAction

internal data class UpdateAction(
    override val state: State,
    val isReplay: Boolean = false,
) : StateManagerAction
