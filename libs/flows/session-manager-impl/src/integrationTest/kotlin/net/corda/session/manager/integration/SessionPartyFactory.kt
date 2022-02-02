package net.corda.session.manager.integration

class SessionPartyFactory {

    /**
     * Create two Session Parties.
     */
    fun createSessionParties(): Pair<SessionParty, SessionParty> {
        val alice = SessionParty()
        val bob = SessionParty(alice)
        alice.otherParty = bob

        return Pair(alice, bob)
    }
}