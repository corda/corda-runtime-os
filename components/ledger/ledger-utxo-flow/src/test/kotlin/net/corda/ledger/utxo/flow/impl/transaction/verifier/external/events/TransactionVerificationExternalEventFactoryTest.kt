package net.corda.ledger.utxo.flow.impl.transaction.verifier.external.events

import net.corda.data.KeyValuePairList
import net.corda.data.flow.event.external.ExternalEventContext
import net.corda.flow.state.FlowCheckpoint
import net.corda.ledger.utxo.verification.CordaPackageSummary
import net.corda.ledger.utxo.verification.TransactionVerificationRequest
import net.corda.ledger.utxo.flow.impl.persistence.external.events.ALICE_X500_HOLDING_IDENTITY
import net.corda.schema.Schemas
import net.corda.virtualnode.toCorda
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import java.nio.ByteBuffer
import java.time.Clock
import java.time.Instant
import java.time.ZoneId

class TransactionVerificationExternalEventFactoryTest {
    @Test
    fun `creates a record containing an VerifyContractsRequest`() {
        val checkpoint = mock<FlowCheckpoint>()
        val transaction = ByteBuffer.wrap(byteArrayOf(1))
        val cpiMetadata = CordaPackageSummary(
            "cpi",
            "1.0",
            "SHA-256:0000000000000001",
            "SHA-256:0000000000000011"
        )
        val externalEventContext = ExternalEventContext(
            "request id",
            "flow id",
            KeyValuePairList(emptyList())
        )
        val testClock = Clock.fixed(Instant.now(), ZoneId.of("UTC"))

        whenever(checkpoint.holdingIdentity).thenReturn(ALICE_X500_HOLDING_IDENTITY.toCorda())

        val externalEventRecord = TransactionVerificationExternalEventFactory(testClock).createExternalEvent(
            checkpoint,
            externalEventContext,
            TransactionVerificationParameters(transaction, cpiMetadata)
        )

        assertEquals(Schemas.Verification.VERIFICATION_LEDGER_PROCESSOR_TOPIC, externalEventRecord.topic)
        assertNull(externalEventRecord.key)
        assertEquals(
            TransactionVerificationRequest(
                testClock.instant(),
                ALICE_X500_HOLDING_IDENTITY,
                transaction,
                cpiMetadata,
                externalEventContext
            ),
            externalEventRecord.payload
        )
    }
}
