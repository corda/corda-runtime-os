package net.corda.ledger.notary.worker.selection.impl

import net.corda.v5.base.types.MemberX500Name
import net.corda.v5.ledger.common.Party
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock

class NotaryWorkerSelectorServiceImplTest {

    companion object {
        private val NOTARY_WORKER_1 = Party(MemberX500Name.parse("O=notary1, L=London, C=GB"), mock())
        private val NOTARY_WORKER_2 = Party(MemberX500Name.parse("O=notary2, L=London, C=GB"), mock())
        private val NOTARY_WORKER_3 = Party(MemberX500Name.parse("O=notary3, L=London, C=GB"), mock())
        private val NOTARY_WORKER_4 = Party(MemberX500Name.parse("O=notary4, L=London, C=GB"), mock())
    }

    @Test
    fun `select notary workers in a round-robin`() {
        val list = listOf(NOTARY_WORKER_1, NOTARY_WORKER_2, NOTARY_WORKER_3)
        val selector = NotaryWorkerSelectorServiceImpl()

        assertEquals(list[0], selector.next(list))
        assertEquals(list[1], selector.next(list))
        assertEquals(list[2], selector.next(list))

        assertEquals(list[0], selector.next(list))
        assertEquals(list[1], selector.next(list))
        assertEquals(list[2], selector.next(list))

        assertEquals(list[0], selector.next(list))
        assertEquals(list[1], selector.next(list))
        assertEquals(list[2], selector.next(list))
    }

    @Test
    fun `select notary workers in a round-robin even if the worker count changes`() {
        val list = listOf(NOTARY_WORKER_1, NOTARY_WORKER_2, NOTARY_WORKER_3)
        val increasedList = listOf(NOTARY_WORKER_1, NOTARY_WORKER_2, NOTARY_WORKER_3, NOTARY_WORKER_4)
        val decreasedList = listOf(NOTARY_WORKER_1, NOTARY_WORKER_2)

        val selector = NotaryWorkerSelectorServiceImpl()

        assertEquals(list[0], selector.next(list))
        assertEquals(list[1], selector.next(list))
        assertEquals(list[2], selector.next(list))
        assertEquals(list[0], selector.next(list))
        assertEquals(list[1], selector.next(list))
        assertEquals(list[2], selector.next(list))

        // Add a new notary worker to the list (size=4)
        assertEquals(increasedList[0], selector.next(increasedList))
        assertEquals(increasedList[1], selector.next(increasedList))
        assertEquals(increasedList[2], selector.next(increasedList))
        assertEquals(increasedList[3], selector.next(increasedList))
        assertEquals(increasedList[0], selector.next(increasedList))
        assertEquals(increasedList[1], selector.next(increasedList))
        assertEquals(increasedList[2], selector.next(increasedList))
        assertEquals(increasedList[3], selector.next(increasedList))

        // Remove a notary worker from the original list (size=2)
        assertEquals(decreasedList[0], selector.next(decreasedList))
        assertEquals(decreasedList[1], selector.next(decreasedList))
        assertEquals(decreasedList[0], selector.next(decreasedList))
        assertEquals(decreasedList[1], selector.next(decreasedList))
    }
}
