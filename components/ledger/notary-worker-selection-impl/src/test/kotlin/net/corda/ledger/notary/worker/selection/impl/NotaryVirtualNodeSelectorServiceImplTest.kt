package net.corda.ledger.notary.worker.selection.impl

import net.corda.membership.lib.MemberInfoExtension
import net.corda.membership.lib.notary.MemberNotaryDetails
import net.corda.v5.application.membership.MemberLookup
import net.corda.v5.base.types.MemberX500Name
import net.corda.v5.ledger.common.Party
import net.corda.v5.membership.MGMContext
import net.corda.v5.membership.MemberContext
import net.corda.v5.membership.MemberInfo
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock

class NotaryVirtualNodeSelectorServiceImplTest {

    private companion object {
        const val SELECTION_COUNT = 100
        private val firstNotaryServiceIdentity = Party(
            MemberX500Name.parse("O=MyNotaryService, L=London, C=GB"),
            mock()
        )

        private val secondNotaryServiceIdentity = Party(
            MemberX500Name.Companion.parse("O=MySecondNotaryService, L=London, C=GB"),
            mock()
        )

        val NOTARY_WORKER_1 = mockNotaryVirtualNode("O=notary1, L=London, C=GB", firstNotaryServiceIdentity)
        val NOTARY_WORKER_2 = mockNotaryVirtualNode("O=notary2, L=London, C=GB", secondNotaryServiceIdentity)
        val NOTARY_WORKER_3 = mockNotaryVirtualNode("O=notary3, L=London, C=GB", firstNotaryServiceIdentity)
        val NOTARY_WORKER_4 = mockNotaryVirtualNode("O=notary4, L=London, C=GB", secondNotaryServiceIdentity)

        private val memberLookup = mock<MemberLookup> {
            on { lookup() } doReturn
                    listOf(NOTARY_WORKER_1, NOTARY_WORKER_2, NOTARY_WORKER_3, NOTARY_WORKER_4)
        }

        private val selector = NotaryVirtualNodeSelectorServiceImpl(memberLookup)

        fun mockNotaryVirtualNode(memberName: String, notary: Party): MemberInfo {
            val mockNotaryDetails = MemberNotaryDetails(
                notary.name,
                null,
                emptyList()
            )
            val mockMemberContext: MemberContext = mock {
                on { entries } doReturn mapOf(
                    "${MemberInfoExtension.ROLES_PREFIX}.0" to MemberInfoExtension.NOTARY_ROLE
                ).entries
                on { parse(eq("corda.notary"), eq(MemberNotaryDetails::class.java)) } doReturn mockNotaryDetails
            }
            val mgmContext: MGMContext = mock()

            return mock {
                on { name } doReturn MemberX500Name.parse(memberName)
                on { sessionInitiationKey } doReturn mock()
                on { memberProvidedContext } doReturn mockMemberContext
                on { mgmProvidedContext } doReturn mgmContext
            }
        }
    }

    @Test
    fun `select notary virtual nodes randomly`() {
        // It's hard to test that it is actually random so we'll just check the distribution of selected elements to
        // make sure none of the elements is chosen exclusively
        val selectedVirtualNodes = mutableMapOf<Party, Int>()

        (1..SELECTION_COUNT).forEach { _ ->
            val selected = selector.selectVirtualNode(firstNotaryServiceIdentity)
            selectedVirtualNodes.merge(selected, 1, Int::plus)
        }

        assertThat(selectedVirtualNodes.size).isEqualTo(2)
        assertThat(selectedVirtualNodes).doesNotContainValue(0)
        assertThat(selectedVirtualNodes).doesNotContainValue(SELECTION_COUNT)
    }

    @Test
    fun `only select virtual nodes that belong to the given service`() {
        val selectedVirtualNodesForFirstService = mutableMapOf<Party, Int>()
        val selectedVirtualNodesForSecondService = mutableMapOf<Party, Int>()

        (1..SELECTION_COUNT).forEach { _ ->
            selectedVirtualNodesForFirstService.merge(
                selector.selectVirtualNode(firstNotaryServiceIdentity),
                1,
                Int::plus
            )

            selectedVirtualNodesForSecondService.merge(
                selector.selectVirtualNode(secondNotaryServiceIdentity),
                1,
                Int::plus
            )
        }

        assertThat(selectedVirtualNodesForFirstService).containsOnlyKeys(
            Party(NOTARY_WORKER_1.name, NOTARY_WORKER_1.sessionInitiationKey),
            Party(NOTARY_WORKER_3.name, NOTARY_WORKER_3.sessionInitiationKey)
        )
        assertThat(selectedVirtualNodesForSecondService).containsOnlyKeys(
            Party(NOTARY_WORKER_2.name, NOTARY_WORKER_2.sessionInitiationKey),
            Party(NOTARY_WORKER_4.name, NOTARY_WORKER_4.sessionInitiationKey)
        )
    }
}
