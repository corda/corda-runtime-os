package net.corda.ledger.utxo.flow.impl.verification.external.events

import net.corda.data.KeyValuePairList
import net.corda.data.flow.event.external.ExternalEventContext
import net.corda.flow.state.FlowCheckpoint
import net.corda.ledger.utxo.contract.verification.CordaPackageSummary
import net.corda.ledger.utxo.contract.verification.VerifyContractsRequest
import net.corda.ledger.utxo.flow.impl.persistence.external.events.ALICE_X500_HOLDING_IDENTITY
import net.corda.ledger.utxo.flow.impl.verification.events.VerifyContractsExternalEventFactory
import net.corda.ledger.utxo.flow.impl.verification.events.VerifyContractsParameters
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

class VerifyContractsExternalEventFactoryTest {
    @Test
    fun `creates a record containing an VerifyContractsRequest`() {
        val checkpoint = mock<FlowCheckpoint>()
        val transaction = ByteBuffer.wrap(byteArrayOf(1))
        val cpkMetadata = listOf(
            CordaPackageSummary(
                "cpk1",
                "1.0",
                "SHA-256:0000000000000001",
                "SHA-256:0000000000000011"
            ),
            CordaPackageSummary(
                "cpk2",
                "2.0",
                "SHA-256:0000000000000002",
                "SHA-256:0000000000000022"
            )
        )
        val externalEventContext = ExternalEventContext(
            "request id",
            "flow id",
            KeyValuePairList(emptyList())
        )
        val testClock = Clock.fixed(Instant.now(), ZoneId.of("UTC"))

        whenever(checkpoint.holdingIdentity).thenReturn(ALICE_X500_HOLDING_IDENTITY.toCorda())

        val externalEventRecord = VerifyContractsExternalEventFactory(testClock).createExternalEvent(
            checkpoint,
            externalEventContext,
            VerifyContractsParameters(transaction, cpkMetadata)
        )

        assertEquals(Schemas.Verification.VERIFICATION_LEDGER_PROCESSOR_TOPIC, externalEventRecord.topic)
        assertNull(externalEventRecord.key)
        assertEquals(
            VerifyContractsRequest(
                testClock.instant(),
                ALICE_X500_HOLDING_IDENTITY,
                transaction,
                cpkMetadata,
                externalEventContext
            ),
            externalEventRecord.payload
        )
    }
}
