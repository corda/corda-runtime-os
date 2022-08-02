package net.corda.ledger.consensual

import net.corda.v5.ledger.consensual.ConsensualLedgerService
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.api.TestInstance.Lifecycle.PER_CLASS
import org.osgi.test.common.annotation.InjectService

@TestInstance(PER_CLASS)
@Suppress("FunctionName")
class ConsensualLedgerServiceTest {

    @InjectService(timeout = 1000)
    lateinit var consensualLedgerService: ConsensualLedgerService

    @Test
    fun `can inject and use the service`() {
        assertThat(consensualLedgerService.double(21)).isEqualTo(42)
    }
}
