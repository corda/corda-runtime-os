package net.corda.session.manager.integration

class SessionPartyFactory {

    /**
     * Create two Session Parties.
     */
    fun createSessionParties(): Pair<SessionParty, SessionParty> {
        val aliceMessageBus = MessageBus()
        val bobMessageBus = MessageBus()

        val alice = SessionParty(aliceMessageBus, bobMessageBus)
        val bob = SessionParty(bobMessageBus, aliceMessageBus)

        return Pair(alice, bob)
    }
}