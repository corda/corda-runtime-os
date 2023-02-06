package net.cordapp.demo.utxo

import net.corda.application.impl.services.json.JsonMarshallingServiceImpl
import net.corda.v5.application.flows.RPCRequestData
import net.corda.v5.application.flows.getRequestBodyAs
import net.corda.v5.application.marshalling.parse
import net.corda.v5.crypto.SecureHash
import net.corda.v5.ledger.utxo.StateAndRef
import net.corda.v5.ledger.utxo.StateRef
import net.corda.v5.ledger.utxo.TransactionState
import net.corda.v5.ledger.utxo.UtxoLedgerService
import net.corda.v5.ledger.utxo.transaction.UtxoLedgerTransaction
import org.assertj.core.api.Assertions
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import java.security.KeyPairGenerator

class TestPeekTransactionFlow {
    class TestFindTransactionFlow {
        private val jsonMarshallingService = JsonMarshallingServiceImpl()

        @Test
        fun missingTransactionReturnsError() {
            val flow = PeekTransactionFlow()

            val txIdBad = SecureHash("SHA256", "Fail!".toByteArray())
            val ledgerService = mock<UtxoLedgerService>()
            whenever(ledgerService.findLedgerTransaction(txIdBad)).thenReturn(null)

            flow.marshallingService = jsonMarshallingService
            flow.ledgerService = ledgerService

            val badRequest = mock<RPCRequestData>()
            val body = PeekTransactionParameters(txIdBad.toString())
            whenever(badRequest.getRequestBodyAs<PeekTransactionParameters>(jsonMarshallingService)).thenReturn(body)

            val result = flow.call(badRequest)
            val resObj = jsonMarshallingService.parse<PeekTransactionResponse>(result)
            Assertions.assertThat(resObj.inputs).isEmpty()
            Assertions.assertThat(resObj.outputs).isEmpty()
            Assertions.assertThat(resObj.errorMessage)
                .isEqualTo("Failed to load transaction.")
        }

        @Test
        fun canReturnExampleFlowStates() {
            val flow = PeekTransactionFlow()


            val txIdGood = SecureHash("SHA256", "12345".toByteArray())

            val keyGenerator = KeyPairGenerator.getInstance("EC")

            val participantKey = keyGenerator.generateKeyPair().public
            val testState = UtxoDemoFlow.TestUtxoState("text", listOf(participantKey), listOf(""))
            val testContractState = mock<TransactionState<UtxoDemoFlow.TestUtxoState>>().apply {
                whenever(this.contractState).thenReturn(testState)
            }

            val testStateAndRef = mock<StateAndRef<UtxoDemoFlow.TestUtxoState>>().apply {
                whenever(ref).thenReturn(StateRef(txIdGood, 0))
                whenever(state).thenReturn(testContractState )
            }


            val ledgerTx = mock<UtxoLedgerTransaction>().apply {
                whenever(id).thenReturn(txIdGood)
                whenever(getOutputStateAndRefs(UtxoDemoFlow.TestUtxoState::class.java))
                    .thenReturn(listOf(testStateAndRef))
                whenever(signatories).thenReturn(listOf(testState.participants.first()))
            }

            val ledgerService = mock<UtxoLedgerService>()
            whenever(ledgerService.findLedgerTransaction(txIdGood)).thenReturn(ledgerTx)

            flow.marshallingService = jsonMarshallingService
            flow.ledgerService = ledgerService

            val goodRequest = mock<RPCRequestData>()
            val body = PeekTransactionParameters(txIdGood.toString())
            whenever(goodRequest.getRequestBodyAs<PeekTransactionParameters>(jsonMarshallingService)).thenReturn(body)

            val result = flow.call(goodRequest)
            val resObj = jsonMarshallingService.parse<PeekTransactionResponse>(result)
            Assertions.assertThat(resObj.inputs).isEmpty()
            Assertions.assertThat(resObj.outputs).hasSize(1)
            Assertions.assertThat(resObj.outputs.first().testField).isEqualTo("text")
            Assertions.assertThat(resObj.errorMessage).isNull()
        }

    }
}