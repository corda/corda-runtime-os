package net.corda.ledger.notary.worker.selection.impl

import net.corda.membership.read.NotaryVirtualNodeLookup
import net.corda.v5.base.types.MemberX500Name
import net.corda.v5.ledger.common.Party
import net.corda.v5.membership.MemberInfo
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Assertions.assertEquals
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

    private lateinit var notaryServiceIdentity: Party
    private lateinit var virtualNodeLookup: NotaryVirtualNodeLookup

    @BeforeEach
    fun setup() {
        notaryServiceIdentity = Party(
            MemberX500Name.parse("O=MyNotaryService, L=London, C=GB"),
            mock()
        )

        virtualNodeLookup = mock {
            on { getNotaryVirtualNodes(eq(notaryServiceIdentity.name)) } doReturn
                    listOf(NOTARY_WORKER_1, NOTARY_WORKER_2, NOTARY_WORKER_3, NOTARY_WORKER_4)
        }
    }

    @Test
    fun `select notary virtual nodes randomly`() {
        // It's hard to test that it is actually random so we'll just check the distribution of selected elements to
        // make sure none of the elements is chosen exclusively
        val selector = NotaryVirtualNodeSelectorServiceImpl(virtualNodeLookup)

        val selectedVirtualNodes = mutableMapOf<Party, Int>()

        (1..SELECTION_COUNT).forEach { _ ->
            val selected = selector.next(notaryServiceIdentity)
            selectedVirtualNodes.merge(selected, 1, Int::plus)
        }

        assertThat(selectedVirtualNodes.size).isEqualTo(4)
        assertThat(selectedVirtualNodes).doesNotContainValue(0)
        assertThat(selectedVirtualNodes).doesNotContainValue(SELECTION_COUNT)
    }
}
