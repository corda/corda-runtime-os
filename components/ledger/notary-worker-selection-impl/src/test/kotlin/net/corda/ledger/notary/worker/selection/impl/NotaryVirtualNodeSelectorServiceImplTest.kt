package net.corda.ledger.notary.worker.selection.impl

import net.corda.membership.read.NotaryVirtualNodeLookup
import net.corda.v5.base.types.MemberX500Name
import net.corda.v5.ledger.common.Party
import net.corda.v5.membership.MemberInfo
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock

class NotaryVirtualNodeSelectorServiceImplTest {

    private companion object {
        const val SELECTION_COUNT = 100
        val NOTARY_WORKER_1 = mockNotaryVirtualNode("O=notary1, L=London, C=GB")
        val NOTARY_WORKER_2 = mockNotaryVirtualNode("O=notary2, L=London, C=GB")
        val NOTARY_WORKER_3 = mockNotaryVirtualNode("O=notary3, L=London, C=GB")
        val NOTARY_WORKER_4 = mockNotaryVirtualNode("O=notary4, L=London, C=GB")

        fun mockNotaryVirtualNode(memberName: String): MemberInfo {
            return mock {
                on { name } doReturn MemberX500Name.parse(memberName)
                on { sessionInitiationKey } doReturn mock()
            }
        }
    }

    private lateinit var firstNotaryServiceIdentity: Party
    private lateinit var secondNotaryServiceIdentity: Party
    private lateinit var virtualNodeLookup: NotaryVirtualNodeLookup

    @BeforeEach
    fun setup() {
        firstNotaryServiceIdentity = Party(
            MemberX500Name.parse("O=MyNotaryService, L=London, C=GB"),
            mock()
        )

        secondNotaryServiceIdentity = Party(
            MemberX500Name.Companion.parse("O=MySecondNotaryService, L=London, C=GB"),
            mock()
        )

        virtualNodeLookup = mock {
            on { getNotaryVirtualNodes(eq(firstNotaryServiceIdentity.name)) } doReturn
                    listOf(NOTARY_WORKER_1, NOTARY_WORKER_3)

            on { getNotaryVirtualNodes(eq(secondNotaryServiceIdentity.name)) } doReturn
                    listOf(NOTARY_WORKER_2, NOTARY_WORKER_4)
        }
    }

    @Test
    fun `select notary virtual nodes randomly`() {
        // It's hard to test that it is actually random so we'll just check the distribution of selected elements to
        // make sure none of the elements is chosen exclusively
        val selector = NotaryVirtualNodeSelectorServiceImpl(virtualNodeLookup)

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
        val selector = NotaryVirtualNodeSelectorServiceImpl(virtualNodeLookup)

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
