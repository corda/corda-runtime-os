package net.corda.ledger.utxo.flow.impl.flows.transactionbuilder.v2

import net.corda.ledger.utxo.flow.impl.flows.backchain.TransactionBackchainSenderFlow
import net.corda.ledger.utxo.flow.impl.flows.transactionbuilder.SendTransactionBuilderDiffFlowCommonTest
import net.corda.ledger.utxo.flow.impl.persistence.UtxoLedgerPersistenceService
import net.corda.ledger.utxo.flow.impl.persistence.UtxoLedgerStateQueryService
import net.corda.ledger.utxo.flow.impl.transaction.UtxoBaselinedTransactionBuilder
import net.corda.ledger.utxo.flow.impl.transaction.UtxoTransactionBuilderContainer
import net.corda.ledger.utxo.testkit.anotherNotaryX500Name
import net.corda.ledger.utxo.testkit.notaryX500Name
import net.corda.v5.ledger.common.NotaryLookup
import net.corda.v5.ledger.utxo.StateAndRef
import net.corda.v5.ledger.utxo.TransactionState
import net.corda.v5.ledger.utxo.transaction.filtered.UtxoFilteredTransactionAndSignatures
import net.corda.v5.membership.NotaryInfo
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.mockito.kotlin.any
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.security.PublicKey

@Suppress("MaxLineLength")
class SendTransactionBuilderDiffFlowV2Test : SendTransactionBuilderDiffFlowCommonTest() {

    private val notaryLookup = mock<NotaryLookup>()
    private val utxoLedgerPersistenceService = mock<UtxoLedgerPersistenceService>()
    private val ledgerStateQueryService = mock<UtxoLedgerStateQueryService>()

    private val notaryKey = mock<PublicKey>()
    private val notaryInfo = mock<NotaryInfo> {
        on { isBackchainRequired } doReturn true
        on { publicKey } doReturn notaryKey
        on { name } doReturn notaryX500Name
    }

    private val txState1 = mock<TransactionState<*>> {
        on { notaryName } doReturn notaryX500Name
    }
    private val txState2 = mock<TransactionState<*>> {
        on { notaryName } doReturn notaryX500Name
    }

    private val stateAndRef1 = mock<StateAndRef<*>> {
        on { state } doReturn txState1
    }

    private val stateAndRef2 = mock<StateAndRef<*>> {
        on { state } doReturn txState2
    }

    private val filteredTxAndSigs1 = mock<UtxoFilteredTransactionAndSignatures>()
    private val filteredTxAndSigs2 = mock<UtxoFilteredTransactionAndSignatures>()

    @BeforeEach
    fun setup() {
        whenever(notaryLookup.lookup(eq(notaryX500Name))).thenReturn(notaryInfo)
        whenever(ledgerStateQueryService.resolveStateRefs(listOf(stateRef2))).thenReturn(
            listOf(stateAndRef2)
        )

        whenever(ledgerStateQueryService.resolveStateRefs(listOf(stateRef1, stateRef2))).thenReturn(
            listOf(stateAndRef2)
        )
    }

    @Test
    fun `called with no dependencies will only send the transaction builder`() {
        callSendFlow()

        verify(session).send(TransactionBuilderPayload(UtxoTransactionBuilderContainer()))
    }

    @Test
    fun `called with dependencies that have backchain verifying notary will initiate backchain resolution`() {
        whenever(notaryLookup.lookup(notaryX500Name)).thenReturn(notaryInfo)
        whenever(ledgerStateQueryService.resolveStateRefs(listOf(stateRef1, stateRef2))).thenReturn(
            listOf(stateAndRef1, stateAndRef2)
        )
        whenever(currentTransactionBuilder.inputStateRefs).thenReturn(listOf(stateRef1))
        whenever(currentTransactionBuilder.referenceStateRefs).thenReturn(listOf(stateRef2))

        callSendFlow()

        verify(flowEngine).subFlow(any<TransactionBackchainSenderFlow>())
    }

    @Test
    fun `called with dependencies that have contract verifying notary will send filtered transactions`() {
        whenever(notaryInfo.isBackchainRequired).thenReturn(false)
        whenever(notaryLookup.lookup(notaryX500Name)).thenReturn(notaryInfo)
        whenever(ledgerStateQueryService.resolveStateRefs(listOf(stateRef1, stateRef2))).thenReturn(
            listOf(stateAndRef1, stateAndRef2)
        )
        whenever(currentTransactionBuilder.inputStateRefs).thenReturn(listOf(stateRef1))
        whenever(currentTransactionBuilder.referenceStateRefs).thenReturn(listOf(stateRef2))

        whenever(
            utxoLedgerPersistenceService.findFilteredTransactionsAndSignatures(listOf(stateRef1, stateRef2), notaryKey, notaryX500Name)
        )
            .thenReturn(
                mapOf(
                    stateRef1.transactionId to filteredTxAndSigs1,
                    stateRef2.transactionId to filteredTxAndSigs2,
                )
            )
        callSendFlow()

        verify(session).send(
            TransactionBuilderPayload(
                UtxoBaselinedTransactionBuilder(currentTransactionBuilder).diff(),
                listOf(filteredTxAndSigs1, filteredTxAndSigs2)
            )
        )
        verify(flowEngine, never()).subFlow(any<TransactionBackchainSenderFlow>())
    }

    @Test
    fun `called with dependencies that have different notaries will throw an error`() {
        whenever(notaryInfo.isBackchainRequired).thenReturn(false)
        whenever(notaryLookup.lookup(notaryX500Name)).thenReturn(notaryInfo)
        whenever(ledgerStateQueryService.resolveStateRefs(listOf(stateRef1, stateRef2))).thenReturn(
            listOf(stateAndRef1, stateAndRef2)
        )
        whenever(currentTransactionBuilder.inputStateRefs).thenReturn(listOf(stateRef1))
        whenever(currentTransactionBuilder.referenceStateRefs).thenReturn(listOf(stateRef2))

        whenever(txState1.notaryName).thenReturn(notaryX500Name)
        whenever(txState2.notaryName).thenReturn(anotherNotaryX500Name)

        val ex = assertThrows<IllegalArgumentException> {
            callSendFlow()
        }

        assertThat(ex).hasStackTraceContaining("Every dependency needs to have the same notary")
    }

    @Test
    fun `called with unkown notary will throw error`() {
        whenever(notaryInfo.isBackchainRequired).thenReturn(false)
        whenever(notaryLookup.lookup(notaryX500Name)).thenReturn(notaryInfo)
        whenever(ledgerStateQueryService.resolveStateRefs(listOf(stateRef1, stateRef2))).thenReturn(
            listOf(stateAndRef1, stateAndRef2)
        )
        whenever(currentTransactionBuilder.inputStateRefs).thenReturn(listOf(stateRef1))
        whenever(currentTransactionBuilder.referenceStateRefs).thenReturn(listOf(stateRef2))

        whenever(
            utxoLedgerPersistenceService.findFilteredTransactionsAndSignatures(listOf(stateRef1, stateRef2), notaryKey, notaryX500Name)
        )
            .thenReturn(emptyMap())

        val ex = assertThrows<IllegalArgumentException> {
            callSendFlow()
        }

        assertThat(ex).hasStackTraceContaining("The number of filtered transactions didn't match the number of dependencies")
    }

    @Test
    fun `called with mismatching dependency and filtered dependency size will throw an error`() {
        whenever(notaryLookup.lookup(notaryX500Name)).thenReturn(null)
        whenever(ledgerStateQueryService.resolveStateRefs(listOf(stateRef1, stateRef2))).thenReturn(
            listOf(stateAndRef1, stateAndRef2)
        )
        whenever(currentTransactionBuilder.inputStateRefs).thenReturn(listOf(stateRef1))
        whenever(currentTransactionBuilder.referenceStateRefs).thenReturn(listOf(stateRef2))

        val ex = assertThrows<IllegalArgumentException> {
            callSendFlow()
        }

        assertThat(ex).hasStackTraceContaining("Could not find notary service with name: $notaryX500Name")
    }

    override fun callSendFlow() {
        val flow = SendTransactionBuilderDiffFlowV2(
            UtxoBaselinedTransactionBuilder(currentTransactionBuilder).diff(),
            session
        ).also {
            it.notaryLookup = notaryLookup
            it.persistenceService = utxoLedgerPersistenceService
            it.ledgerStateQueryService = ledgerStateQueryService
        }

        flow.flowEngine = flowEngine
        flow.call()
    }

    override val payloadWrapper = TransactionBuilderPayload::class.java
}
