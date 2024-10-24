package net.corda.ledger.utxo.flow.impl.transaction.verifier.external.events

import net.corda.flow.external.events.ExternalEventContext
import net.corda.flow.state.FlowCheckpoint
import net.corda.flow.utils.toAvro
import net.corda.ledger.common.data.transaction.CordaPackageSummaryImpl
import net.corda.ledger.utxo.flow.impl.persistence.external.events.ALICE_X500_HOLDING_IDENTITY
import net.corda.ledger.utxo.flow.impl.transaction.verifier.TransactionVerificationExternalEventFactory
import net.corda.ledger.utxo.flow.impl.transaction.verifier.TransactionVerificationParameters
import net.corda.ledger.utxo.flow.impl.transaction.verifier.toAvro
import net.corda.ledger.utxo.verification.TransactionVerificationRequest
import net.corda.v5.ledger.common.transaction.CordaPackageSummary
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
        val transaction = byteArrayOf(1)
        val cpkMetadata = listOf(
            CordaPackageSummaryImpl(
                "cpk1",
                "1.0",
                "SHA-256:0000000000000001",
                "SHA-256:0000000000000011"
            ),
            CordaPackageSummaryImpl(
                "cpk2",
                "2.0",
                "SHA-256:0000000000000002",
                "SHA-256:0000000000000022"
            )
        )
        val externalEventContext = ExternalEventContext(
            "request id",
            "flow id",
            emptyMap()
        )
        val testClock = Clock.fixed(Instant.now(), ZoneId.of("UTC"))

        whenever(checkpoint.holdingIdentity).thenReturn(ALICE_X500_HOLDING_IDENTITY.toCorda())

        val externalEventRecord = TransactionVerificationExternalEventFactory(testClock).createExternalEvent(
            checkpoint,
            externalEventContext,
            TransactionVerificationParameters(transaction, cpkMetadata)
        )

        assertNull(externalEventRecord.key)
        assertEquals(
            TransactionVerificationRequest(
                testClock.instant(),
                ALICE_X500_HOLDING_IDENTITY,
                ByteBuffer.wrap(transaction),
                cpkMetadata.map(CordaPackageSummary::toAvro),
                externalEventContext.toAvro()
            ),
            externalEventRecord.payload
        )
    }
}
