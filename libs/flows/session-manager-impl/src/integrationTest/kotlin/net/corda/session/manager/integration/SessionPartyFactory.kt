package net.corda.session.manager.integration

import net.corda.data.flow.state.session.SessionStateType
import net.corda.libs.configuration.SmartConfig
import net.corda.test.flow.util.buildSessionState

class SessionPartyFactory {

    /**
     * Create two Session Parties.
     */
    fun createSessionParties(config: SmartConfig): Pair<SessionParty, SessionParty> {
        val aliceMessageBus = MessageBus()
        val bobMessageBus = MessageBus()

        val alice = SessionParty(aliceMessageBus, bobMessageBus, config, buildSessionState(SessionStateType.CREATED, 0, emptyList(), 0, emptyList()))
        val bob = SessionParty(bobMessageBus, aliceMessageBus, config, null)

        return Pair(alice, bob)
    }
}