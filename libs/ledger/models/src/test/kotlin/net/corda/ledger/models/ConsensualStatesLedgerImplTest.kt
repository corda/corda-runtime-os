package net.corda.ledger.models

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class ConsensualStatesLedgerImplTest {
    @Test
    fun `Test basic behaviour`() {
        val service = ConsensualStatesLedgerImpl()
        val res = service.double(4)
        assertEquals(8, res)
    }
}