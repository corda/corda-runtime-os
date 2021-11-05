package net.corda.membership.application.converter

import net.corda.membership.application.PartyImpl
import net.corda.membership.conversion.PropertyConverterImpl
import net.corda.membership.identity.MGMContextImpl
import net.corda.membership.identity.MemberContextImpl
import net.corda.membership.identity.MemberInfoExtension
import net.corda.membership.identity.converter.PublicKeyConverter
import net.corda.v5.application.identity.CordaX500Name
import net.corda.v5.application.identity.Party
import net.corda.v5.cipher.suite.KeyEncodingService
import net.corda.v5.membership.identity.MemberX500Name
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Mockito
import org.mockito.kotlin.whenever
import java.security.PublicKey
import kotlin.test.assertFailsWith

class PartyConverterTest {
    companion object {
        private val keyEncodingService = Mockito.mock(KeyEncodingService::class.java)
        private const val PARTY = "corda.party"
        private const val partyName = "O=Alice,L=London,C=GB"
        private const val notaryName = "O=Notary,L=London,C=GB"
        private const val NOTARY_SERVICE_PARTY = "corda.notaryServiceParty"
        private const val KEY = "12345"
        private val key = Mockito.mock(PublicKey::class.java)

        private val converter = PropertyConverterImpl(listOf(PartyConverter(), PublicKeyConverter(keyEncodingService)))

        val memberContext = MemberContextImpl(
            sortedMapOf(
                MemberInfoExtension.PARTY_NAME to partyName,
                MemberInfoExtension.PARTY_OWNING_KEY to KEY,
                MemberInfoExtension.NOTARY_SERVICE_PARTY_NAME to notaryName,
                MemberInfoExtension.NOTARY_SERVICE_PARTY_KEY to KEY
            ),
            converter
        )

        val nodeParty = PartyImpl(
            CordaX500Name(MemberX500Name.parse(partyName)),
            key
        )

        val notaryServiceParty = PartyImpl(
            CordaX500Name(MemberX500Name.parse(notaryName)),
            key
        )

        val mgmContext = MGMContextImpl(
            sortedMapOf(
                MemberInfoExtension.PARTY_NAME to partyName,
                MemberInfoExtension.PARTY_OWNING_KEY to KEY
            ),
            converter
        )
    }

    @BeforeEach
    fun setUp() {
        whenever(
            keyEncodingService.decodePublicKey(KEY)
        ).thenReturn(key)
        whenever(
            keyEncodingService.encodeAsString(key)
        ).thenReturn(KEY)
    }

    @Test
    fun `PartyConverter works for converting node's party`() {
        assertEquals(nodeParty, memberContext.parse(PARTY, Party::class.java))
    }

    @Test
    fun `PartyConverter works for converting notary service's party`() {
        assertEquals(notaryServiceParty, memberContext.parse(NOTARY_SERVICE_PARTY, Party::class.java))
    }

    @Test
    fun `PartyConverter fails when incorrect context is used`() {
        val ex = assertFailsWith<IllegalArgumentException> { mgmContext.parse(PARTY, Party::class.java) }
        assertEquals("Unknown class 'net.corda.membership.identity.MGMContextImpl'.", ex.message)
    }
}