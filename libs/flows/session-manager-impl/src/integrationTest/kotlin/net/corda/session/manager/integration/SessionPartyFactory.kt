package net.corda.session.manager.integration

import net.corda.libs.configuration.SmartConfig

class SessionPartyFactory {

    /**
     * Create two Session Parties.
     */
    fun createSessionParties(config: SmartConfig): Pair<SessionParty, SessionParty> {
        val aliceMessageBus = MessageBus()
        val bobMessageBus = MessageBus()

        val alice = SessionParty(aliceMessageBus, bobMessageBus, config)
        val bob = SessionParty(bobMessageBus, aliceMessageBus, config)

        return Pair(alice, bob)
    }
}