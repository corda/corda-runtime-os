package net.corda.membership.application.converter

import net.corda.layeredpropertymap.create
import net.corda.membership.application.PartyImpl
import net.corda.layeredpropertymap.testkit.LayeredPropertyMapMocks
import net.corda.membership.identity.MemberContextImpl
import net.corda.membership.identity.MemberInfoExtension
import net.corda.v5.application.identity.Party
import net.corda.v5.base.types.MemberX500Name
import net.corda.v5.cipher.suite.CipherSchemeMetadata
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Mockito
import org.mockito.kotlin.whenever
import java.security.PublicKey

class PartyConverterTest {
    companion object {
        private val keyEncodingService = Mockito.mock(CipherSchemeMetadata::class.java)
        private const val PARTY = "corda.party"
        private const val partyName = "O=Alice,L=London,C=GB"
        private const val notaryName = "O=Notary,L=London,C=GB"
        private const val NOTARY_SERVICE_PARTY = "corda.notaryServiceParty"
        private const val KEY = "12345"
        private val key = Mockito.mock(PublicKey::class.java)

        private val converters = listOf(PartyConverter(keyEncodingService))
        private val layeredPropertyMapFactory = LayeredPropertyMapMocks.createFactory(converters)

        val memberContext = layeredPropertyMapFactory.create<MemberContextImpl>(
            sortedMapOf(
                MemberInfoExtension.PARTY_NAME to partyName,
                MemberInfoExtension.PARTY_OWNING_KEY to KEY,
                MemberInfoExtension.NOTARY_SERVICE_PARTY_NAME to notaryName,
                MemberInfoExtension.NOTARY_SERVICE_PARTY_KEY to KEY
            )
        )

        val nodeParty = PartyImpl(
            MemberX500Name.parse(partyName),
            key
        )

        val notaryServiceParty = PartyImpl(
            MemberX500Name.parse(notaryName),
            key
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
}