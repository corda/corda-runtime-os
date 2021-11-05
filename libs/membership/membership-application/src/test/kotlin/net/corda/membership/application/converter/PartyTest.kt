package net.corda.membership.application.converter

import net.corda.membership.application.AnonymousPartyImpl
import net.corda.membership.application.PartyImpl
import net.corda.v5.application.identity.AbstractParty
import net.corda.v5.application.identity.CordaX500Name
import net.corda.v5.membership.identity.MemberX500Name
import org.junit.jupiter.api.Test
import org.mockito.Mockito
import java.security.PublicKey
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertNull

class PartyTest {
    companion object {
        private const val partyName = "O=Alice,L=London,C=GB"
        private val cordaX500Name = CordaX500Name(MemberX500Name.parse(partyName))
        private val key = Mockito.mock(PublicKey::class.java)
        private val anotherKey = Mockito.mock(PublicKey::class.java)
    }

    @Test
    fun `create Party object`() {
        val party = PartyImpl(cordaX500Name, key)
        assertEquals(key, party.owningKey)
        assertEquals(cordaX500Name, party.name)
        val anonymousParty = party.anonymise()
        assertEquals<AbstractParty>(party, anonymousParty)
        val anotherParty = PartyImpl(cordaX500Name, anotherKey)
        assertNotEquals(party, anotherParty)
    }

    @Test
    fun `create AnonymousParty object`() {
        val anonymousParty = AnonymousPartyImpl(key)
        assertEquals(key, anonymousParty.owningKey)
        assertNull(anonymousParty.nameOrNull())
        val anotherAnonymousParty = AnonymousPartyImpl(anotherKey)
        assertNotEquals(anonymousParty, anotherAnonymousParty)
    }
}