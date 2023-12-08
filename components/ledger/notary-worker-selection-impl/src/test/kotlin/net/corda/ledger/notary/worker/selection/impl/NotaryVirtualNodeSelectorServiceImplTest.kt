package net.corda.ledger.notary.worker.selection.impl

import net.corda.membership.lib.MemberInfoExtension
import net.corda.membership.lib.notary.MemberNotaryDetails
import net.corda.v5.application.membership.MemberLookup
import net.corda.v5.base.types.MemberX500Name
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
        private val firstNotaryServiceName = MemberX500Name.parse("O=MyNotaryService, L=London, C=GB")

        private val secondNotaryServiceName = MemberX500Name.parse("O=MySecondNotaryService, L=London, C=GB")

        val NOTARY_WORKER_1 = mockNotaryVirtualNode("O=notary1, L=London, C=GB", firstNotaryServiceName)
        val NOTARY_WORKER_2 = mockNotaryVirtualNode("O=notary2, L=London, C=GB", secondNotaryServiceName)
        val NOTARY_WORKER_3 = mockNotaryVirtualNode("O=notary3, L=London, C=GB", firstNotaryServiceName)
        val NOTARY_WORKER_4 = mockNotaryVirtualNode("O=notary4, L=London, C=GB", secondNotaryServiceName)

        private val memberLookup = mock<MemberLookup> {
            on { lookup() } doReturn
                    listOf(NOTARY_WORKER_1, NOTARY_WORKER_2, NOTARY_WORKER_3, NOTARY_WORKER_4)
        }

        private val selector = NotaryVirtualNodeSelectorServiceImpl(memberLookup)

        fun mockNotaryVirtualNode(memberName: String, notary: MemberX500Name): MemberInfo {
            val mockNotaryDetails = MemberNotaryDetails(
                notary,
                null,
                emptyList(),
                emptyList(),
                true
            )
            val mockMemberContext: MemberContext = mock {
                on { entries } doReturn mapOf(
                    "${MemberInfoExtension.ROLES_PREFIX}.0" to MemberInfoExtension.NOTARY_ROLE
                ).entries
                on { parse(eq("corda.notary"), eq(MemberNotaryDetails::class.java)) } doReturn mockNotaryDetails
            }
            return mock {
                on { name } doReturn MemberX500Name.parse(memberName)
                on { ledgerKeys } doReturn listOf(mock())
                on { memberProvidedContext } doReturn mockMemberContext
            }
        }
    }

    @Test
    fun `select notary virtual nodes randomly`() {
        // It's hard to test that it is actually random so we'll just check the distribution of selected elements to
        // make sure none of the elements is chosen exclusively
        val selectedVirtualNodes = mutableMapOf<MemberX500Name, Int>()

        for (i in 1..SELECTION_COUNT) {
            val selected = selector.selectVirtualNode(firstNotaryServiceName)
            selectedVirtualNodes.merge(selected, 1, Int::plus)
        }

        assertThat(selectedVirtualNodes.size).isEqualTo(2)
        assertThat(selectedVirtualNodes).doesNotContainValue(0)
        assertThat(selectedVirtualNodes).doesNotContainValue(SELECTION_COUNT)
    }

    @Test
    fun `only select virtual nodes that belong to the given service`() {
        val selectedVirtualNodesForFirstService = mutableMapOf<MemberX500Name, Int>()
        val selectedVirtualNodesForSecondService = mutableMapOf<MemberX500Name, Int>()

        for (i in 1..SELECTION_COUNT) {
            selectedVirtualNodesForFirstService.merge(
                selector.selectVirtualNode(firstNotaryServiceName),
                1,
                Int::plus
            )

            selectedVirtualNodesForSecondService.merge(
                selector.selectVirtualNode(secondNotaryServiceName),
                1,
                Int::plus
            )
        }

        assertThat(selectedVirtualNodesForFirstService).containsOnlyKeys(
            NOTARY_WORKER_1.name,
            NOTARY_WORKER_3.name
        )
        assertThat(selectedVirtualNodesForSecondService).containsOnlyKeys(
            NOTARY_WORKER_2.name,
            NOTARY_WORKER_4.name
        )
    }
}
