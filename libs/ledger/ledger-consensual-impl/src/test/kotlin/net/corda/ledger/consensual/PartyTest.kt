package net.corda.ledger.consensual

import net.corda.v5.base.types.MemberX500Name
import org.junit.jupiter.api.Test
import org.mockito.Mockito
import java.security.PublicKey
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals

class PartyTest {
    companion object {
        private const val partyName = "O=Alice,L=London,C=GB"
        private val memberX500Name = MemberX500Name.parse(partyName)
        private val key = Mockito.mock(PublicKey::class.java)
        private val anotherKey = Mockito.mock(PublicKey::class.java)
    }

    @Test
    fun `create Party object`() {
        val party = PartyImpl(memberX500Name, key)
        assertEquals(key, party.owningKey)
        assertEquals(memberX500Name, party.name)

        val anotherParty = PartyImpl(memberX500Name, anotherKey)
        assertNotEquals(party, anotherParty)
    }
}