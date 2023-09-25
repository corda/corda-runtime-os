package net.corda.session.manager.integration

import net.corda.data.flow.state.session.SessionStateType
import net.corda.flow.utils.INITIATED_SESSION_ID_SUFFIX
import net.corda.libs.configuration.SmartConfig
import net.corda.test.flow.util.buildSessionState

class SessionPartyFactory {

    /**
     * Create two Session Parties.
     */
    fun createSessionParties(config: SmartConfig): Pair<SessionParty, SessionParty> {
        val aliceMessageBus = MessageBus()
        val bobMessageBus = MessageBus()

        val alice = SessionParty(aliceMessageBus, bobMessageBus, config, buildSessionState(SessionStateType.CREATED, 0, emptyList(), 0,
            emptyList()), isInitiating = true)
        val bob = SessionParty(bobMessageBus, aliceMessageBus, config, buildSessionState(SessionStateType.CONFIRMED, 0, emptyList(), 0,
            emptyList(), sessionId = "ssesionId$INITIATED_SESSION_ID_SUFFIX"), isInitiating = false)

        return Pair(alice, bob)
    }
}