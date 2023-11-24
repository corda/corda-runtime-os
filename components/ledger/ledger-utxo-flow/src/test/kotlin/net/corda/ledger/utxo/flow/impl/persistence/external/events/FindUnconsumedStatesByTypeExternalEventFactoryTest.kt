package net.corda.ledger.utxo.flow.impl.persistence.external.events

import net.corda.data.KeyValuePairList
import net.corda.data.flow.event.external.ExternalEventContext
import net.corda.data.ledger.persistence.FindUnconsumedStatesByType
import net.corda.data.ledger.persistence.LedgerPersistenceRequest
import net.corda.data.ledger.persistence.LedgerTypes
import net.corda.flow.state.FlowCheckpoint
import net.corda.v5.ledger.utxo.ContractState
import net.corda.virtualnode.toCorda
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import java.security.PublicKey
import java.time.Clock
import java.time.Instant
import java.time.ZoneId

class FindUnconsumedStatesByTypeExternalEventFactoryTest {

    class TestContractState : ContractState {
        override fun getParticipants(): List<PublicKey> {
            return emptyList()
        }
    }

    @Test
    fun `creates a record containing an UtxoLedgerRequest with a FindUnconsumedStatesByType payload`() {
        val checkpoint = mock<FlowCheckpoint>()
        val stateClass = TestContractState()::class.java
        val externalEventContext = ExternalEventContext(
            "request id",
            "flow id",
            KeyValuePairList(emptyList())
        )
        val testClock = Clock.fixed(Instant.now(), ZoneId.of("UTC"))

        whenever(checkpoint.holdingIdentity).thenReturn(ALICE_X500_HOLDING_IDENTITY.toCorda())

        val externalEventRecord = FindUnconsumedStatesByTypeExternalEventFactory(testClock).createExternalEvent(
            checkpoint,
            externalEventContext,
            FindUnconsumedStatesByTypeParameters(stateClass)
        )

        assertNull(externalEventRecord.key)
        assertEquals(
            LedgerPersistenceRequest(
                testClock.instant(),
                ALICE_X500_HOLDING_IDENTITY,
                LedgerTypes.UTXO,
                FindUnconsumedStatesByType(stateClass.canonicalName),
                externalEventContext
            ),
            externalEventRecord.payload
        )
    }
}
