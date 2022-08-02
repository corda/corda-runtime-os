package net.corda.ledger.consensual

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class ConsensualLedgerServiceImplTest {
    @Test
    fun `Test basic behaviour`() {
        val service = ConsensualLedgerServiceImpl()
        val res = service.double(4)
        assertEquals(8, res)
    }
}
