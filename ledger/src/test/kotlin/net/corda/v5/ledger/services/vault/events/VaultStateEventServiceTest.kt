package net.corda.v5.ledger.services.vault.events

import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import net.corda.v5.base.stream.DurableCursor
import net.corda.v5.base.util.contextLogger
import net.corda.v5.ledger.contracts.ContractState
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.time.Duration
import java.time.temporal.ChronoUnit

/**
 * Tests the [VaultStateEventService] API is usable from Kotlin.
 */
class VaultStateEventServiceTest {

    private companion object {
        val log = contextLogger()
    }

    private val cursor: DurableCursor<VaultStateEvent<ContractState>> = mock()

    private val vaultStateEventService: VaultStateEventService = mock()

    @BeforeEach
    fun setup() {
        whenever(vaultStateEventService.subscribe(any())).thenReturn(cursor)
    }

    @Test
    fun `subscribe cursor`() {
        val cursor: DurableCursor<VaultStateEvent<ContractState>> = vaultStateEventService.subscribe("give me a cursor")
        cursor.poll(50, Duration.of(5, ChronoUnit.MINUTES))
        cursor.commit(1)
    }

    @Test
    fun `subscribe callback`() {
        vaultStateEventService.subscribe("give me a cursor") { deduplicationId, event ->
            log.info("Processing event: $event with deduplication id: $deduplicationId")
        }
    }
}