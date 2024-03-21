package net.corda.ledger.utxo.flow.impl.flows.repair

import net.corda.ledger.common.data.transaction.TransactionStatus.INVALID
import net.corda.ledger.common.data.transaction.TransactionStatus.UNVERIFIED
import net.corda.ledger.common.data.transaction.TransactionStatus.VERIFIED
import net.corda.ledger.common.flow.transaction.TransactionMissingSignaturesException
import net.corda.ledger.utxo.flow.impl.notary.PluggableNotaryDetails
import net.corda.ledger.utxo.flow.impl.notary.PluggableNotaryService
import net.corda.ledger.utxo.flow.impl.persistence.UtxoLedgerPersistenceService
import net.corda.ledger.utxo.flow.impl.transaction.UtxoSignedTransactionInternal
import net.corda.ledger.utxo.impl.token.selection.impl.ALICE_X500_NAME
import net.corda.ledger.utxo.impl.token.selection.impl.BOB_X500_NAME
import net.corda.utilities.minutes
import net.corda.utilities.time.Clock
import net.corda.v5.application.crypto.DigitalSignatureAndMetadata
import net.corda.v5.application.flows.FlowEngine
import net.corda.v5.base.types.MemberX500Name
import net.corda.v5.crypto.SecureHash
import net.corda.v5.ledger.common.transaction.TransactionSignatureException
import net.corda.v5.ledger.notary.plugin.api.NotarizationType.CHECK
import net.corda.v5.ledger.notary.plugin.api.PluggableNotaryClientFlow
import net.corda.v5.ledger.notary.plugin.core.NotaryExceptionFatal
import net.corda.v5.ledger.notary.plugin.core.NotaryExceptionGeneral
import net.corda.v5.ledger.notary.plugin.core.NotaryExceptionUnknown
import net.corda.v5.ledger.utxo.VisibilityChecker
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.doNothing
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.time.Duration
import java.time.Instant

class UtxoLedgerRepairFlowTest {

    private companion object {
        private val NOW = Instant.now()
        private val MAX_DURATION_WITHOUT_SUSPENDING = 2.minutes
        private val TX_ID_1 = mock<SecureHash>()
        private val TX_ID_2 = mock<SecureHash>()
        private val TX_ID_3 = mock<SecureHash>()
        private val SIGNATURE = DigitalSignatureAndMetadata(mock(), mock(), mock())
        private val CHARLIE_X500_NAME = MemberX500Name.parse("CN=Charlie, O=Charlie Corp, L=LDN, C=GB")
        private val PLUGGABLE_NOTARY_DETAILS = PluggableNotaryDetails(PluggableNotaryClientFlow::class.java, false)
    }

    private val clock = mock<Clock>()
    private val flowEngine = mock<FlowEngine>()
    private val persistenceService = mock<UtxoLedgerPersistenceService>()
    private val pluggableNotaryService = mock<PluggableNotaryService>()
    private val visibilityChecker = mock<VisibilityChecker>()

    private val transaction1 = mock<UtxoSignedTransactionInternal>()
    private val transaction2 = mock<UtxoSignedTransactionInternal>()
    private val transaction3 = mock<UtxoSignedTransactionInternal>()

    private val pluggableNotaryFlow1 = mock<PluggableNotaryClientFlow>()
    private val pluggableNotaryFlow2 = mock<PluggableNotaryClientFlow>()
    private val pluggableNotaryFlow3 = mock<PluggableNotaryClientFlow>()

    private val notaryExceptionGeneral = object : NotaryExceptionGeneral("", TX_ID_1) {}
    private val notaryExceptionUnknown = object : NotaryExceptionUnknown("", TX_ID_1) {}
    private val notaryExceptionFatal = object : NotaryExceptionFatal("", TX_ID_1) {}

    private interface PluggableNotaryClientFlow2 : PluggableNotaryClientFlow
    private interface PluggableNotaryClientFlow3 : PluggableNotaryClientFlow

    @BeforeEach
    fun beforeEach() {
        whenever(persistenceService.findTransactionsWithStatusCreatedBetweenTime(eq(UNVERIFIED), any(), any(), any()))
            .thenReturn(listOf(TX_ID_1, TX_ID_2, TX_ID_3))
        whenever(persistenceService.findSignedTransaction(TX_ID_1, UNVERIFIED)).thenReturn(transaction1)
        whenever(persistenceService.findSignedTransaction(TX_ID_2, UNVERIFIED)).thenReturn(transaction2)
        whenever(persistenceService.findSignedTransaction(TX_ID_3, UNVERIFIED)).thenReturn(transaction3)

        whenever(transaction1.notaryName).thenReturn(ALICE_X500_NAME)
        whenever(transaction2.notaryName).thenReturn(ALICE_X500_NAME)
        whenever(transaction3.notaryName).thenReturn(ALICE_X500_NAME)

        whenever(pluggableNotaryService.get(ALICE_X500_NAME)).thenReturn(PLUGGABLE_NOTARY_DETAILS)
        whenever(pluggableNotaryService.create(transaction1, PLUGGABLE_NOTARY_DETAILS, CHECK)).thenReturn(pluggableNotaryFlow1)
        whenever(pluggableNotaryService.create(transaction2, PLUGGABLE_NOTARY_DETAILS, CHECK)).thenReturn(pluggableNotaryFlow1)
        whenever(pluggableNotaryService.create(transaction3, PLUGGABLE_NOTARY_DETAILS, CHECK)).thenReturn(pluggableNotaryFlow1)

        whenever(flowEngine.subFlow(pluggableNotaryFlow1)).thenReturn(listOf(SIGNATURE))

        whenever(clock.instant()).thenReturn(NOW)
    }

    @Test
    fun `exits when the duration is exceeded before repairing any transactions`() {
        // Initial [lastCallToNotaryTime] and duration check
        whenever(clock.instant()).thenReturn(NOW, NOW.plusSeconds(10))
        val utxoLedgerRepairFlow = createUtxoLedgerRepairFlow()
        val result = utxoLedgerRepairFlow.call()
        assertThat(result.exceededDuration).isTrue()
        assertThat(result.exceededLastNotarizationTime).isFalse()
        assertThat(result.numberOfNotarizedTransactions).isZero()
        assertThat(result.numberOfNotNotarizedTransactions).isZero()
        assertThat(result.numberOfInvalidTransactions).isZero()
        assertThat(result.numberOfSkippedTransactions).isZero()
        verify(clock, times(2)).instant()
    }

    @Test
    fun `exits when the duration is exceeded before notarizing a transaction`() {
        // Initial [lastCallToNotaryTime] and duration check x2
        whenever(clock.instant()).thenReturn(NOW, NOW, NOW.plusSeconds(10))
        whenever(transaction1.verifyAttachedNotarySignature()).thenThrow(TransactionSignatureException(TX_ID_1, "", RuntimeException()))
        val utxoLedgerRepairFlow = createUtxoLedgerRepairFlow()
        val result = utxoLedgerRepairFlow.call()
        assertThat(result.exceededDuration).isTrue()
        assertThat(result.exceededLastNotarizationTime).isFalse()
        assertThat(result.numberOfNotarizedTransactions).isZero()
        assertThat(result.numberOfNotNotarizedTransactions).isZero()
        assertThat(result.numberOfInvalidTransactions).isZero()
        assertThat(result.numberOfSkippedTransactions).isZero()
        verify(clock, times(3)).instant()
    }

    @Test
    fun `exits when the duration is exceeded before incrementing repair count`() {
        // Initial [lastCallToNotaryTime], duration check x2, updating [lastCallToNotaryTime] and duration check
        whenever(clock.instant()).thenReturn(NOW, NOW, NOW, NOW, NOW.plusSeconds(10))
        whenever(transaction1.verifyAttachedNotarySignature()).thenThrow(TransactionSignatureException(TX_ID_1, "", RuntimeException()))
        whenever(flowEngine.subFlow(pluggableNotaryFlow1)).thenThrow(notaryExceptionUnknown)
        val utxoLedgerRepairFlow = createUtxoLedgerRepairFlow()
        val result = utxoLedgerRepairFlow.call()
        assertThat(result.exceededDuration).isTrue()
        assertThat(result.exceededLastNotarizationTime).isFalse()
        assertThat(result.numberOfNotarizedTransactions).isZero()
        assertThat(result.numberOfNotNotarizedTransactions).isOne()
        assertThat(result.numberOfInvalidTransactions).isZero()
        assertThat(result.numberOfSkippedTransactions).isZero()
        verify(persistenceService, never()).incrementTransactionRepairAttemptCount(any())
        verify(clock, times(5)).instant()
    }

    @Test
    fun `exits when the duration is exceeded after repairing transactions`() {
        // Initial [lastCallToNotaryTime], duration check x2, updating [lastCallToNotaryTime] and duration check x2
        whenever(clock.instant()).thenReturn(NOW, NOW, NOW, NOW, NOW, NOW.plusSeconds(10))
        // Returning one so that the loop tries to re-query which hits the duration check
        whenever(persistenceService.findTransactionsWithStatusCreatedBetweenTime(eq(UNVERIFIED), any(), any(), any()))
            .thenReturn(listOf(TX_ID_1))
        whenever(transaction1.verifyAttachedNotarySignature()).thenThrow(TransactionSignatureException(TX_ID_1, "", RuntimeException()))
        whenever(flowEngine.subFlow(pluggableNotaryFlow1)).thenThrow(notaryExceptionUnknown)
        val utxoLedgerRepairFlow = createUtxoLedgerRepairFlow(queryLimit = 1)
        val result = utxoLedgerRepairFlow.call()
        assertThat(result.exceededDuration).isTrue()
        assertThat(result.exceededLastNotarizationTime).isFalse()
        assertThat(result.numberOfNotarizedTransactions).isZero()
        assertThat(result.numberOfNotNotarizedTransactions).isOne()
        assertThat(result.numberOfInvalidTransactions).isZero()
        assertThat(result.numberOfSkippedTransactions).isZero()
        verify(persistenceService).incrementTransactionRepairAttemptCount(any())
        verify(clock, times(6)).instant()
    }

    @Test
    fun `exits when the last notarization time is exceeded before repairing any transactions`() {
        // Initial [lastCallToNotaryTime] and duration check
        whenever(clock.instant()).thenReturn(NOW.minus(MAX_DURATION_WITHOUT_SUSPENDING.plusSeconds(1)), NOW)
        val utxoLedgerRepairFlow = createUtxoLedgerRepairFlow()
        val result = utxoLedgerRepairFlow.call()
        assertThat(result.exceededDuration).isFalse()
        assertThat(result.exceededLastNotarizationTime).isTrue()
        assertThat(result.numberOfNotarizedTransactions).isZero()
        assertThat(result.numberOfNotNotarizedTransactions).isZero()
        assertThat(result.numberOfInvalidTransactions).isZero()
        assertThat(result.numberOfSkippedTransactions).isZero()
        verify(clock, times(2)).instant()
    }

    @Test
    fun `exits when the last notarization time is exceeded before notarizing a transaction`() {
        // Initial [lastCallToNotaryTime] and duration check x2
        whenever(clock.instant()).thenReturn(
            NOW.minus(MAX_DURATION_WITHOUT_SUSPENDING.plusSeconds(1)),
            NOW.minus(MAX_DURATION_WITHOUT_SUSPENDING.plusSeconds(1)),
            NOW
        )
        whenever(transaction1.verifyAttachedNotarySignature()).thenThrow(TransactionSignatureException(TX_ID_1, "", RuntimeException()))
        val utxoLedgerRepairFlow = createUtxoLedgerRepairFlow()
        val result = utxoLedgerRepairFlow.call()
        assertThat(result.exceededDuration).isFalse()
        assertThat(result.exceededLastNotarizationTime).isTrue()
        assertThat(result.numberOfNotarizedTransactions).isZero()
        assertThat(result.numberOfNotNotarizedTransactions).isZero()
        assertThat(result.numberOfInvalidTransactions).isZero()
        assertThat(result.numberOfSkippedTransactions).isZero()
        verify(clock, times(3)).instant()
    }

    @Test
    fun `exits when the last notarization time is exceeded before incrementing repair count`() {
        // Initial [lastCallToNotaryTime], duration check x2, updating [lastCallToNotaryTime] and duration check
        whenever(clock.instant()).thenReturn(
            NOW.minus(MAX_DURATION_WITHOUT_SUSPENDING.plusSeconds(1)),
            NOW.minus(MAX_DURATION_WITHOUT_SUSPENDING.plusSeconds(1)),
            NOW.minus(MAX_DURATION_WITHOUT_SUSPENDING.plusSeconds(1)),
            NOW.minus(MAX_DURATION_WITHOUT_SUSPENDING.plusSeconds(1)),
            NOW
        )
        whenever(transaction1.verifyAttachedNotarySignature()).thenThrow(TransactionSignatureException(TX_ID_1, "", RuntimeException()))
        whenever(flowEngine.subFlow(pluggableNotaryFlow1)).thenThrow(notaryExceptionUnknown)
        val utxoLedgerRepairFlow = createUtxoLedgerRepairFlow()
        val result = utxoLedgerRepairFlow.call()
        assertThat(result.exceededDuration).isFalse()
        assertThat(result.exceededLastNotarizationTime).isTrue()
        assertThat(result.numberOfNotarizedTransactions).isZero()
        assertThat(result.numberOfNotNotarizedTransactions).isOne()
        assertThat(result.numberOfInvalidTransactions).isZero()
        assertThat(result.numberOfSkippedTransactions).isZero()
        verify(persistenceService, never()).incrementTransactionRepairAttemptCount(any())
        verify(clock, times(5)).instant()
    }

    @Test
    fun `exits when the last notarization time is exceeded after repairing transactions`() {
        // Initial [lastCallToNotaryTime], duration check x2, updating [lastCallToNotaryTime] and duration check x2
        whenever(clock.instant()).thenReturn(
            NOW.minus(MAX_DURATION_WITHOUT_SUSPENDING.plusSeconds(1)),
            NOW.minus(MAX_DURATION_WITHOUT_SUSPENDING.plusSeconds(1)),
            NOW.minus(MAX_DURATION_WITHOUT_SUSPENDING.plusSeconds(1)),
            NOW.minus(MAX_DURATION_WITHOUT_SUSPENDING.plusSeconds(1)),
            NOW.minus(MAX_DURATION_WITHOUT_SUSPENDING.plusSeconds(1)),
            NOW
        )
        // Returning one so that the loop tries to re-query which hits the duration check
        whenever(persistenceService.findTransactionsWithStatusCreatedBetweenTime(eq(UNVERIFIED), any(), any(), any()))
            .thenReturn(listOf(TX_ID_1))
        whenever(transaction1.verifyAttachedNotarySignature()).thenThrow(TransactionSignatureException(TX_ID_1, "", RuntimeException()))
        whenever(flowEngine.subFlow(pluggableNotaryFlow1)).thenThrow(notaryExceptionUnknown)
        val utxoLedgerRepairFlow = createUtxoLedgerRepairFlow(queryLimit = 1)
        val result = utxoLedgerRepairFlow.call()
        assertThat(result.exceededDuration).isFalse()
        assertThat(result.exceededLastNotarizationTime).isTrue()
        assertThat(result.numberOfNotarizedTransactions).isZero()
        assertThat(result.numberOfNotNotarizedTransactions).isOne()
        assertThat(result.numberOfInvalidTransactions).isZero()
        assertThat(result.numberOfSkippedTransactions).isZero()
        verify(persistenceService).incrementTransactionRepairAttemptCount(any())
        verify(clock, times(6)).instant()
    }

    @Test
    fun `last notarization time is updated when a transaction goes to the notary and returns a NotaryExceptionGeneral`() {
        // Initial [lastCallToNotaryTime], duration check x2, updating [lastCallToNotaryTime] and duration check
        whenever(clock.instant()).thenReturn(
            NOW.minus(MAX_DURATION_WITHOUT_SUSPENDING.plusSeconds(1)),
            NOW.minus(MAX_DURATION_WITHOUT_SUSPENDING.plusSeconds(1)),
            NOW.minus(MAX_DURATION_WITHOUT_SUSPENDING.plusSeconds(1)),
            NOW,
            NOW
        )
        whenever(persistenceService.findTransactionsWithStatusCreatedBetweenTime(eq(UNVERIFIED), any(), any(), any()))
            .thenReturn(listOf(TX_ID_1))
        whenever(transaction1.verifyAttachedNotarySignature()).thenThrow(TransactionSignatureException(TX_ID_1, "", RuntimeException()))
        whenever(flowEngine.subFlow(pluggableNotaryFlow1)).thenThrow(notaryExceptionGeneral)
        val utxoLedgerRepairFlow = createUtxoLedgerRepairFlow()
        val result = utxoLedgerRepairFlow.call()
        assertThat(result.exceededDuration).isFalse()
        assertThat(result.exceededLastNotarizationTime).isFalse()
        assertThat(result.numberOfNotarizedTransactions).isZero()
        assertThat(result.numberOfNotNotarizedTransactions).isOne()
        assertThat(result.numberOfInvalidTransactions).isZero()
        assertThat(result.numberOfSkippedTransactions).isZero()
        verify(clock, times(5)).instant()
    }

    @Test
    fun `last notarization time is updated when a transaction goes to the notary and returns a NotaryExceptionUnknown`() {
        // Initial [lastCallToNotaryTime], duration check x2, updating [lastCallToNotaryTime] and duration check
        whenever(clock.instant()).thenReturn(
            NOW.minus(MAX_DURATION_WITHOUT_SUSPENDING.plusSeconds(1)),
            NOW.minus(MAX_DURATION_WITHOUT_SUSPENDING.plusSeconds(1)),
            NOW.minus(MAX_DURATION_WITHOUT_SUSPENDING.plusSeconds(1)),
            NOW,
            NOW
        )
        whenever(persistenceService.findTransactionsWithStatusCreatedBetweenTime(eq(UNVERIFIED), any(), any(), any()))
            .thenReturn(listOf(TX_ID_1))
        whenever(transaction1.verifyAttachedNotarySignature()).thenThrow(TransactionSignatureException(TX_ID_1, "", RuntimeException()))
        whenever(flowEngine.subFlow(pluggableNotaryFlow1)).thenThrow(notaryExceptionUnknown)
        val utxoLedgerRepairFlow = createUtxoLedgerRepairFlow()
        val result = utxoLedgerRepairFlow.call()
        assertThat(result.exceededDuration).isFalse()
        assertThat(result.exceededLastNotarizationTime).isFalse()
        assertThat(result.numberOfNotarizedTransactions).isZero()
        assertThat(result.numberOfNotNotarizedTransactions).isOne()
        assertThat(result.numberOfInvalidTransactions).isZero()
        assertThat(result.numberOfSkippedTransactions).isZero()
        verify(clock, times(5)).instant()
    }

    @Test
    fun `last notarization time is updated when a transaction goes to the notary and returns a NotaryExceptionFatal`() {
        // Initial [lastCallToNotaryTime], duration check x2 and updating [lastCallToNotaryTime]
        whenever(clock.instant()).thenReturn(
            NOW.minus(MAX_DURATION_WITHOUT_SUSPENDING.plusSeconds(1)),
            NOW.minus(MAX_DURATION_WITHOUT_SUSPENDING.plusSeconds(1)),
            NOW.minus(MAX_DURATION_WITHOUT_SUSPENDING.plusSeconds(1)),
            NOW,
        )
        whenever(persistenceService.findTransactionsWithStatusCreatedBetweenTime(eq(UNVERIFIED), any(), any(), any()))
            .thenReturn(listOf(TX_ID_1))
        whenever(transaction1.verifyAttachedNotarySignature()).thenThrow(TransactionSignatureException(TX_ID_1, "", RuntimeException()))
        whenever(flowEngine.subFlow(pluggableNotaryFlow1)).thenThrow(notaryExceptionFatal)
        val utxoLedgerRepairFlow = createUtxoLedgerRepairFlow()
        val result = utxoLedgerRepairFlow.call()
        assertThat(result.exceededDuration).isFalse()
        assertThat(result.exceededLastNotarizationTime).isFalse()
        assertThat(result.numberOfNotarizedTransactions).isZero()
        assertThat(result.numberOfNotNotarizedTransactions).isZero()
        assertThat(result.numberOfInvalidTransactions).isOne()
        assertThat(result.numberOfSkippedTransactions).isZero()
        verify(clock, times(4)).instant()
    }

    @Test
    fun `does nothing when there are no unverified transactions created within specified time period`() {
        whenever(persistenceService.findTransactionsWithStatusCreatedBetweenTime(eq(UNVERIFIED), any(), any(), any()))
            .thenReturn(emptyList())
        val utxoLedgerRepairFlow = createUtxoLedgerRepairFlow()
        val result = utxoLedgerRepairFlow.call()
        assertThat(result.exceededDuration).isFalse()
        assertThat(result.exceededLastNotarizationTime).isFalse()
        assertThat(result.numberOfNotarizedTransactions).isZero()
        assertThat(result.numberOfNotNotarizedTransactions).isZero()
        assertThat(result.numberOfInvalidTransactions).isZero()
        assertThat(result.numberOfSkippedTransactions).isZero()
        verify(persistenceService, never()).findSignedTransaction(any(), eq(UNVERIFIED))
    }

    @Test
    fun `does not repair transactions that were unverified but changed status when findSignedTransaction is called`() {
        whenever(persistenceService.findSignedTransaction(TX_ID_1, UNVERIFIED)).thenReturn(transaction1)
        whenever(persistenceService.findSignedTransaction(TX_ID_2, UNVERIFIED)).thenReturn(null)
        whenever(persistenceService.findSignedTransaction(TX_ID_3, UNVERIFIED)).thenReturn(null)
        whenever(transaction1.verifyAttachedNotarySignature()).thenThrow(TransactionSignatureException(TX_ID_1, "", RuntimeException()))
        val utxoLedgerRepairFlow = createUtxoLedgerRepairFlow()
        val result = utxoLedgerRepairFlow.call()
        assertThat(result.exceededDuration).isFalse()
        assertThat(result.exceededLastNotarizationTime).isFalse()
        assertThat(result.numberOfNotarizedTransactions).isOne()
        assertThat(result.numberOfNotNotarizedTransactions).isZero()
        assertThat(result.numberOfInvalidTransactions).isZero()
        assertThat(result.numberOfSkippedTransactions).isEqualTo(2)
    }

    @Test
    fun `does not repair transactions that are not fully signed by non-notary signatories`() {
        doNothing().whenever(transaction1).verifySignatorySignatures()
        whenever(transaction2.verifySignatorySignatures()).thenThrow(TransactionMissingSignaturesException(TX_ID_2, emptySet(), ""))
        whenever(transaction3.verifySignatorySignatures()).thenThrow(TransactionMissingSignaturesException(TX_ID_3, emptySet(), ""))
        whenever(transaction1.verifyAttachedNotarySignature()).thenThrow(TransactionSignatureException(TX_ID_1, "", RuntimeException()))
        val utxoLedgerRepairFlow = createUtxoLedgerRepairFlow()
        val result = utxoLedgerRepairFlow.call()
        assertThat(result.exceededDuration).isFalse()
        assertThat(result.exceededLastNotarizationTime).isFalse()
        assertThat(result.numberOfNotarizedTransactions).isOne()
        assertThat(result.numberOfNotNotarizedTransactions).isZero()
        assertThat(result.numberOfInvalidTransactions).isZero()
        assertThat(result.numberOfSkippedTransactions).isEqualTo(2)
    }

    @Test
    fun `does not repair transactions that are already signed by the notary signature`() {
        whenever(transaction1.verifyAttachedNotarySignature()).thenThrow(TransactionSignatureException(TX_ID_1, "", RuntimeException()))
        doNothing().whenever(transaction2).verifyAttachedNotarySignature()
        doNothing().whenever(transaction3).verifyAttachedNotarySignature()
        val utxoLedgerRepairFlow = createUtxoLedgerRepairFlow()
        val result = utxoLedgerRepairFlow.call()
        assertThat(result.exceededDuration).isFalse()
        assertThat(result.exceededLastNotarizationTime).isFalse()
        assertThat(result.numberOfNotarizedTransactions).isOne()
        assertThat(result.numberOfNotNotarizedTransactions).isZero()
        assertThat(result.numberOfInvalidTransactions).isZero()
        assertThat(result.numberOfSkippedTransactions).isEqualTo(2)
    }

    @Test
    fun `does not repair and increments the repair counter for transactions that return NotaryExceptionGeneral errors from the notary`() {
        whenever(transaction1.verifyAttachedNotarySignature()).thenThrow(TransactionSignatureException(TX_ID_1, "", RuntimeException()))
        whenever(transaction2.verifyAttachedNotarySignature()).thenThrow(TransactionSignatureException(TX_ID_2, "", RuntimeException()))
        whenever(transaction3.verifyAttachedNotarySignature()).thenThrow(TransactionSignatureException(TX_ID_3, "", RuntimeException()))
        whenever(flowEngine.subFlow(pluggableNotaryFlow1))
            .thenReturn(listOf(SIGNATURE))
            .thenThrow(notaryExceptionGeneral)
            .thenThrow(notaryExceptionGeneral)
        val utxoLedgerRepairFlow = createUtxoLedgerRepairFlow()
        val result = utxoLedgerRepairFlow.call()
        assertThat(result.exceededDuration).isFalse()
        assertThat(result.exceededLastNotarizationTime).isFalse()
        assertThat(result.numberOfNotarizedTransactions).isOne()
        assertThat(result.numberOfNotNotarizedTransactions).isEqualTo(2)
        assertThat(result.numberOfInvalidTransactions).isZero()
        assertThat(result.numberOfSkippedTransactions).isZero()
        verify(persistenceService).incrementTransactionRepairAttemptCount(TX_ID_2)
        verify(persistenceService).incrementTransactionRepairAttemptCount(TX_ID_3)
    }

    @Test
    fun `does not repair and increments the repair counter for transactions that return NotaryExceptionUnknown errors from the notary`() {
        whenever(transaction1.verifyAttachedNotarySignature()).thenThrow(TransactionSignatureException(TX_ID_1, "", RuntimeException()))
        whenever(transaction2.verifyAttachedNotarySignature()).thenThrow(TransactionSignatureException(TX_ID_2, "", RuntimeException()))
        whenever(transaction3.verifyAttachedNotarySignature()).thenThrow(TransactionSignatureException(TX_ID_3, "", RuntimeException()))
        whenever(flowEngine.subFlow(pluggableNotaryFlow1))
            .thenReturn(listOf(SIGNATURE))
            .thenThrow(notaryExceptionUnknown)
            .thenThrow(notaryExceptionUnknown)
        val utxoLedgerRepairFlow = createUtxoLedgerRepairFlow()
        val result = utxoLedgerRepairFlow.call()
        assertThat(result.exceededDuration).isFalse()
        assertThat(result.exceededLastNotarizationTime).isFalse()
        assertThat(result.numberOfNotarizedTransactions).isOne()
        assertThat(result.numberOfNotNotarizedTransactions).isEqualTo(2)
        assertThat(result.numberOfInvalidTransactions).isZero()
        assertThat(result.numberOfSkippedTransactions).isZero()
        verify(persistenceService).incrementTransactionRepairAttemptCount(TX_ID_2)
        verify(persistenceService).incrementTransactionRepairAttemptCount(TX_ID_3)
    }

    @Test
    fun `persists transactions as invalid that return NotaryExceptionFatal errors from the notary`() {
        whenever(transaction1.verifyAttachedNotarySignature()).thenThrow(TransactionSignatureException(TX_ID_1, "", RuntimeException()))
        whenever(transaction2.verifyAttachedNotarySignature()).thenThrow(TransactionSignatureException(TX_ID_2, "", RuntimeException()))
        whenever(transaction3.verifyAttachedNotarySignature()).thenThrow(TransactionSignatureException(TX_ID_3, "", RuntimeException()))
        whenever(flowEngine.subFlow(pluggableNotaryFlow1))
            .thenReturn(listOf(SIGNATURE))
            .thenThrow(notaryExceptionFatal)
            .thenThrow(notaryExceptionFatal)
        val utxoLedgerRepairFlow = createUtxoLedgerRepairFlow()
        val result = utxoLedgerRepairFlow.call()
        assertThat(result.exceededDuration).isFalse()
        assertThat(result.exceededLastNotarizationTime).isFalse()
        assertThat(result.numberOfNotarizedTransactions).isOne()
        assertThat(result.numberOfNotNotarizedTransactions).isZero()
        assertThat(result.numberOfInvalidTransactions).isEqualTo(2)
        assertThat(result.numberOfSkippedTransactions).isZero()
    }

    @Test
    fun `does not repair and increments the repair counter for transactions that return unexpected errors from the notary`() {
        whenever(transaction1.verifyAttachedNotarySignature()).thenThrow(TransactionSignatureException(TX_ID_1, "", RuntimeException()))
        whenever(transaction2.verifyAttachedNotarySignature()).thenThrow(TransactionSignatureException(TX_ID_2, "", RuntimeException()))
        whenever(transaction3.verifyAttachedNotarySignature()).thenThrow(TransactionSignatureException(TX_ID_3, "", RuntimeException()))
        whenever(flowEngine.subFlow(pluggableNotaryFlow1))
            .thenReturn(listOf(SIGNATURE))
            .thenThrow(IllegalStateException())
            .thenThrow(RuntimeException())
        val utxoLedgerRepairFlow = createUtxoLedgerRepairFlow()
        val result = utxoLedgerRepairFlow.call()
        assertThat(result.exceededDuration).isFalse()
        assertThat(result.exceededLastNotarizationTime).isFalse()
        assertThat(result.numberOfNotarizedTransactions).isOne()
        assertThat(result.numberOfNotNotarizedTransactions).isEqualTo(2)
        assertThat(result.numberOfInvalidTransactions).isZero()
        assertThat(result.numberOfSkippedTransactions).isZero()
        verify(persistenceService).incrementTransactionRepairAttemptCount(TX_ID_2)
        verify(persistenceService).incrementTransactionRepairAttemptCount(TX_ID_3)
    }

    @Test
    fun `persists transactions as invalid that the notary returns a successful response containing no signatures for`() {
        whenever(transaction1.verifyAttachedNotarySignature()).thenThrow(TransactionSignatureException(TX_ID_1, "", RuntimeException()))
        whenever(transaction2.verifyAttachedNotarySignature()).thenThrow(TransactionSignatureException(TX_ID_2, "", RuntimeException()))
        whenever(transaction3.verifyAttachedNotarySignature()).thenThrow(TransactionSignatureException(TX_ID_3, "", RuntimeException()))
        whenever(flowEngine.subFlow(pluggableNotaryFlow1))
            .thenReturn(listOf(SIGNATURE))
            .thenReturn(emptyList())
            .thenReturn(emptyList())
        val utxoLedgerRepairFlow = createUtxoLedgerRepairFlow()
        val result = utxoLedgerRepairFlow.call()
        assertThat(result.exceededDuration).isFalse()
        assertThat(result.exceededLastNotarizationTime).isFalse()
        assertThat(result.numberOfNotarizedTransactions).isOne()
        assertThat(result.numberOfNotNotarizedTransactions).isZero()
        assertThat(result.numberOfInvalidTransactions).isEqualTo(2)
        assertThat(result.numberOfSkippedTransactions).isZero()
        verify(persistenceService).persist(eq(transaction2), eq(INVALID), any())
        verify(persistenceService).persist(eq(transaction3), eq(INVALID), any())
    }

    @Test
    fun `persists transactions as invalid that have invalid notary signatures`() {
        whenever(transaction1.verifyAttachedNotarySignature()).thenThrow(TransactionSignatureException(TX_ID_1, "", RuntimeException()))
        whenever(transaction2.verifyAttachedNotarySignature()).thenThrow(TransactionSignatureException(TX_ID_2, "", RuntimeException()))
        whenever(transaction3.verifyAttachedNotarySignature()).thenThrow(TransactionSignatureException(TX_ID_3, "", RuntimeException()))
        whenever(flowEngine.subFlow(pluggableNotaryFlow1)).thenReturn(listOf(SIGNATURE))
        doNothing().whenever(transaction1).verifyNotarySignature(SIGNATURE)
        whenever(transaction2.verifyNotarySignature(SIGNATURE)).thenThrow(TransactionSignatureException(TX_ID_2, "", RuntimeException()))
        whenever(transaction3.verifyNotarySignature(SIGNATURE)).thenThrow(TransactionSignatureException(TX_ID_3, "", RuntimeException()))
        val utxoLedgerRepairFlow = createUtxoLedgerRepairFlow()
        val result = utxoLedgerRepairFlow.call()
        assertThat(result.exceededDuration).isFalse()
        assertThat(result.exceededLastNotarizationTime).isFalse()
        assertThat(result.numberOfNotarizedTransactions).isOne()
        assertThat(result.numberOfNotNotarizedTransactions).isZero()
        assertThat(result.numberOfInvalidTransactions).isEqualTo(2)
        assertThat(result.numberOfSkippedTransactions).isZero()
        verify(persistenceService).persist(eq(transaction2), eq(INVALID), any())
        verify(persistenceService).persist(eq(transaction3), eq(INVALID), any())
    }

    @Test
    fun `persists transactions as verified that receive successful notary responses`() {
        whenever(transaction1.verifyAttachedNotarySignature()).thenThrow(TransactionSignatureException(TX_ID_1, "", RuntimeException()))
        whenever(transaction2.verifyAttachedNotarySignature()).thenThrow(TransactionSignatureException(TX_ID_2, "", RuntimeException()))
        whenever(transaction3.verifyAttachedNotarySignature()).thenThrow(TransactionSignatureException(TX_ID_3, "", RuntimeException()))
        whenever(flowEngine.subFlow(pluggableNotaryFlow1)).thenReturn(listOf(SIGNATURE))
        doNothing().whenever(transaction1).verifyNotarySignature(SIGNATURE)
        doNothing().whenever(transaction2).verifyNotarySignature(SIGNATURE)
        whenever(transaction3.verifyNotarySignature(SIGNATURE)).thenThrow(TransactionSignatureException(TX_ID_3, "", RuntimeException()))
        val utxoLedgerRepairFlow = createUtxoLedgerRepairFlow()
        val result = utxoLedgerRepairFlow.call()
        assertThat(result.exceededDuration).isFalse()
        assertThat(result.exceededLastNotarizationTime).isFalse()
        assertThat(result.numberOfNotarizedTransactions).isEqualTo(2)
        assertThat(result.numberOfNotNotarizedTransactions).isZero()
        assertThat(result.numberOfInvalidTransactions).isOne()
        assertThat(result.numberOfSkippedTransactions).isZero()
        verify(persistenceService).persist(eq(transaction1), eq(VERIFIED), any())
        verify(persistenceService).persist(eq(transaction2), eq(VERIFIED), any())
    }

    @Test
    fun `repair loop is repeated until duration is exceeded`() {
        var count = 0
        whenever(persistenceService.incrementTransactionRepairAttemptCount(any())).then { count++ }
        whenever(clock.instant()).thenAnswer {
            if (count == 3) {
                NOW.plusSeconds(10)
            } else {
                NOW
            }
        }
        whenever(persistenceService.findTransactionsWithStatusCreatedBetweenTime(eq(UNVERIFIED), any(), any(), any()))
            .thenReturn(listOf(TX_ID_1), listOf(TX_ID_2), listOf(TX_ID_3))
        whenever(transaction1.verifyAttachedNotarySignature()).thenThrow(TransactionSignatureException(TX_ID_1, "", RuntimeException()))
        whenever(transaction2.verifyAttachedNotarySignature()).thenThrow(TransactionSignatureException(TX_ID_2, "", RuntimeException()))
        whenever(transaction3.verifyAttachedNotarySignature()).thenThrow(TransactionSignatureException(TX_ID_3, "", RuntimeException()))
        whenever(flowEngine.subFlow(pluggableNotaryFlow1)).thenThrow(notaryExceptionUnknown)

        val utxoLedgerRepairFlow = createUtxoLedgerRepairFlow(queryLimit = 1)
        val result = utxoLedgerRepairFlow.call()
        assertThat(result.exceededDuration).isTrue()
        assertThat(result.exceededLastNotarizationTime).isFalse()
        assertThat(result.numberOfNotarizedTransactions).isZero()
        assertThat(result.numberOfNotNotarizedTransactions).isEqualTo(3)
        assertThat(result.numberOfInvalidTransactions).isZero()
        assertThat(result.numberOfSkippedTransactions).isZero()
        verify(persistenceService, times(3)).findTransactionsWithStatusCreatedBetweenTime(eq(UNVERIFIED), any(), any(), any())
    }

    @Test
    fun `repair loop is repeated until last notarization time is exceeded`() {
        var count = 0
        whenever(persistenceService.incrementTransactionRepairAttemptCount(any())).then { count++ }
        whenever(clock.instant()).thenAnswer {
            if (count == 3) {
                NOW
            } else {
                NOW.minus(MAX_DURATION_WITHOUT_SUSPENDING.plusSeconds(1))
            }
        }
        whenever(persistenceService.findTransactionsWithStatusCreatedBetweenTime(eq(UNVERIFIED), any(), any(), any()))
            .thenReturn(listOf(TX_ID_1), listOf(TX_ID_2), listOf(TX_ID_3))
        whenever(transaction1.verifyAttachedNotarySignature()).thenThrow(TransactionSignatureException(TX_ID_1, "", RuntimeException()))
        whenever(transaction2.verifyAttachedNotarySignature()).thenThrow(TransactionSignatureException(TX_ID_2, "", RuntimeException()))
        whenever(transaction3.verifyAttachedNotarySignature()).thenThrow(TransactionSignatureException(TX_ID_3, "", RuntimeException()))
        whenever(flowEngine.subFlow(pluggableNotaryFlow1)).thenThrow(notaryExceptionUnknown)

        val utxoLedgerRepairFlow = createUtxoLedgerRepairFlow(queryLimit = 1)
        val result = utxoLedgerRepairFlow.call()
        assertThat(result.exceededDuration).isFalse()
        assertThat(result.exceededLastNotarizationTime).isTrue()
        assertThat(result.numberOfNotarizedTransactions).isZero()
        assertThat(result.numberOfNotNotarizedTransactions).isEqualTo(3)
        assertThat(result.numberOfInvalidTransactions).isZero()
        assertThat(result.numberOfSkippedTransactions).isZero()
        verify(persistenceService, times(3)).findTransactionsWithStatusCreatedBetweenTime(eq(UNVERIFIED), any(), any(), any())
    }

    @Test
    fun `repair loop is repeated until the first not notarized transaction is returned from the query again`() {
        whenever(persistenceService.findTransactionsWithStatusCreatedBetweenTime(eq(UNVERIFIED), any(), any(), any()))
            .thenReturn(listOf(TX_ID_1), listOf(TX_ID_2), listOf(TX_ID_1))
        whenever(transaction1.verifyAttachedNotarySignature()).thenThrow(TransactionSignatureException(TX_ID_1, "", RuntimeException()))
        whenever(transaction2.verifyAttachedNotarySignature()).thenThrow(TransactionSignatureException(TX_ID_2, "", RuntimeException()))
        whenever(flowEngine.subFlow(pluggableNotaryFlow1)).thenThrow(notaryExceptionUnknown)

        val utxoLedgerRepairFlow = createUtxoLedgerRepairFlow(queryLimit = 1)
        val result = utxoLedgerRepairFlow.call()
        assertThat(result.exceededDuration).isFalse()
        assertThat(result.exceededLastNotarizationTime).isFalse()
        assertThat(result.numberOfNotarizedTransactions).isZero()
        assertThat(result.numberOfNotNotarizedTransactions).isEqualTo(2)
        assertThat(result.numberOfInvalidTransactions).isZero()
        assertThat(result.numberOfSkippedTransactions).isZero()
        verify(persistenceService, times(3)).findTransactionsWithStatusCreatedBetweenTime(eq(UNVERIFIED), any(), any(), any())
    }

    @Test
    fun `repair loop is repeated until the transactions returned from the query is smaller than the QUERY_LIMIT`() {
        whenever(persistenceService.findTransactionsWithStatusCreatedBetweenTime(eq(UNVERIFIED), any(), any(), any()))
            .thenReturn(listOf(TX_ID_1, TX_ID_2), listOf(TX_ID_3))
        whenever(transaction1.verifyAttachedNotarySignature()).thenThrow(TransactionSignatureException(TX_ID_1, "", RuntimeException()))
        whenever(transaction2.verifyAttachedNotarySignature()).thenThrow(TransactionSignatureException(TX_ID_2, "", RuntimeException()))
        whenever(transaction3.verifyAttachedNotarySignature()).thenThrow(TransactionSignatureException(TX_ID_3, "", RuntimeException()))
        whenever(flowEngine.subFlow(pluggableNotaryFlow1)).thenThrow(notaryExceptionUnknown)

        val utxoLedgerRepairFlow = createUtxoLedgerRepairFlow(queryLimit = 2)
        val result = utxoLedgerRepairFlow.call()
        assertThat(result.exceededDuration).isFalse()
        assertThat(result.exceededLastNotarizationTime).isFalse()
        assertThat(result.numberOfNotarizedTransactions).isZero()
        assertThat(result.numberOfNotNotarizedTransactions).isEqualTo(3)
        assertThat(result.numberOfInvalidTransactions).isZero()
        assertThat(result.numberOfSkippedTransactions).isZero()
        verify(persistenceService, times(2)).findTransactionsWithStatusCreatedBetweenTime(eq(UNVERIFIED), any(), any(), any())
    }

    @Test
    fun `each transaction can be checked by a different notary`() {
        whenever(persistenceService.findTransactionsWithStatusCreatedBetweenTime(eq(UNVERIFIED), any(), any(), any()))
            .thenReturn(listOf(TX_ID_1), listOf(TX_ID_2), listOf(TX_ID_3), emptyList())
        whenever(transaction1.verifyAttachedNotarySignature()).thenThrow(TransactionSignatureException(TX_ID_1, "", RuntimeException()))
        whenever(transaction2.verifyAttachedNotarySignature()).thenThrow(TransactionSignatureException(TX_ID_2, "", RuntimeException()))
        whenever(transaction3.verifyAttachedNotarySignature()).thenThrow(TransactionSignatureException(TX_ID_3, "", RuntimeException()))
        whenever(transaction1.notaryName).thenReturn(ALICE_X500_NAME)
        whenever(transaction2.notaryName).thenReturn(BOB_X500_NAME)
        whenever(transaction3.notaryName).thenReturn(MemberX500Name.parse("CN=Charlie, O=Charlie Corp, L=LDN, C=GB"))
        whenever(flowEngine.subFlow(pluggableNotaryFlow1)).thenReturn(listOf(SIGNATURE))
        whenever(flowEngine.subFlow(pluggableNotaryFlow2)).thenReturn(listOf(SIGNATURE))
        whenever(flowEngine.subFlow(pluggableNotaryFlow3)).thenReturn(listOf(SIGNATURE))
        val pluggableNotaryDetails2 = PluggableNotaryDetails(PluggableNotaryClientFlow2::class.java, false)
        val pluggableNotaryDetails3 = PluggableNotaryDetails(PluggableNotaryClientFlow3::class.java, false)
        whenever(pluggableNotaryService.get(ALICE_X500_NAME)).thenReturn(PLUGGABLE_NOTARY_DETAILS)
        whenever(pluggableNotaryService.get(BOB_X500_NAME)).thenReturn(pluggableNotaryDetails2)
        whenever(pluggableNotaryService.get(CHARLIE_X500_NAME)).thenReturn(pluggableNotaryDetails3)
        whenever(pluggableNotaryService.create(transaction1, PLUGGABLE_NOTARY_DETAILS, CHECK)).thenReturn(pluggableNotaryFlow1)
        whenever(pluggableNotaryService.create(transaction2, pluggableNotaryDetails2, CHECK)).thenReturn(pluggableNotaryFlow2)
        whenever(pluggableNotaryService.create(transaction3, pluggableNotaryDetails3, CHECK)).thenReturn(pluggableNotaryFlow3)
        whenever(flowEngine.subFlow(pluggableNotaryFlow1)).thenThrow(notaryExceptionUnknown)
        whenever(flowEngine.subFlow(pluggableNotaryFlow2)).thenThrow(notaryExceptionUnknown)
        whenever(flowEngine.subFlow(pluggableNotaryFlow3)).thenThrow(notaryExceptionUnknown)

        val utxoLedgerRepairFlow = createUtxoLedgerRepairFlow(queryLimit = 1)
        val result = utxoLedgerRepairFlow.call()
        assertThat(result.exceededDuration).isFalse()
        assertThat(result.exceededLastNotarizationTime).isFalse()
        assertThat(result.numberOfNotarizedTransactions).isZero()
        assertThat(result.numberOfNotNotarizedTransactions).isEqualTo(3)
        assertThat(result.numberOfInvalidTransactions).isZero()
        assertThat(result.numberOfSkippedTransactions).isZero()
    }

    private fun createUtxoLedgerRepairFlow(
        from: Instant = NOW,
        until: Instant = NOW,
        endTime: Instant = NOW,
        maxTimeWithoutSuspending: Duration = MAX_DURATION_WITHOUT_SUSPENDING,
        queryLimit: Int = 10
    ): UtxoLedgerRepairFlow {
        return UtxoLedgerRepairFlow(
            from,
            until,
            endTime,
            maxTimeWithoutSuspending,
            clock,
            flowEngine,
            persistenceService,
            pluggableNotaryService,
            visibilityChecker,
            queryLimit
        )
    }
}
