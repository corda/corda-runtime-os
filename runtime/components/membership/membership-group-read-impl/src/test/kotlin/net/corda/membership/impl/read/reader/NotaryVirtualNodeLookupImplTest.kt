package net.corda.membership.impl.read.reader

import net.corda.membership.lib.MemberInfoExtension
import net.corda.membership.lib.notary.MemberNotaryDetails
import net.corda.membership.read.MembershipGroupReader
import net.corda.v5.base.types.MemberX500Name
import net.corda.v5.membership.MemberContext
import net.corda.v5.membership.MemberInfo
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock

class NotaryVirtualNodeLookupImplTest {
    private val aliceOne = createMemberInfo(
        MemberX500Name.parse("C=GB, L=London, O=Alice"),
        "Last"
    )
    private val bob = createMemberInfo(null, "Nop")
    private val carol = createMemberInfo(MemberX500Name.parse("C=GB, L=London, O=Carol"), "Nop")
    private val aliceTwo = createMemberInfo(MemberX500Name.parse("C=GB, L=London, O=Alice"), "First")
    private val reader = mock<MembershipGroupReader> {
        on { lookup() } doReturn listOf(
            aliceOne,
            bob,
            carol,
            aliceTwo,
        )
    }
    private val service = NotaryVirtualNodeLookupImpl(reader)

    @Test
    fun `getNotaryVirtualNodes return the correct list of workers`() {
        assertThat(service.getNotaryVirtualNodes(MemberX500Name.parse("C=GB, L=London, O=Alice"))).containsExactly(
            aliceTwo,
            aliceOne,
        )
    }

    private fun createMemberInfo(notaryServiceName: MemberX500Name?, memberName: String): MemberInfo {
        val context: MemberContext = if (notaryServiceName == null) {
            mock {
                on { entries } doReturn emptySet()
            }
        } else {
            val map = mapOf("${MemberInfoExtension.ROLES_PREFIX}.0" to MemberInfoExtension.NOTARY_ROLE)
            val details = mock<MemberNotaryDetails> {
                on { serviceName } doReturn notaryServiceName
            }
            mock {
                on { entries } doReturn map.entries
                on { parse("corda.notary", MemberNotaryDetails::class.java) } doReturn details
            }
        }
        return mock {
            on { memberProvidedContext } doReturn context
            on { name } doReturn MemberX500Name.parse("C=GB, L=London, O=$memberName")
        }
    }
}
