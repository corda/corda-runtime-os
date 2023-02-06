package net.cordapp.demo.utxo

import net.corda.application.impl.services.json.JsonMarshallingServiceImpl
import net.corda.v5.application.flows.RestRequestBody
import net.corda.v5.application.flows.getRequestBodyAs
import net.corda.v5.application.marshalling.parse
import net.corda.v5.crypto.SecureHash
import net.corda.v5.ledger.utxo.UtxoLedgerService
import net.corda.v5.ledger.utxo.transaction.UtxoLedgerTransaction
import net.cordapp.demo.utxo.contract.TestUtxoState
import org.assertj.core.api.Assertions
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import java.security.KeyPairGenerator

class TestFindTransactionFlow {
    private val jsonMarshallingService = JsonMarshallingServiceImpl()

    @Test
    fun missingTransactionReturnsNull(){
        val flow = FindTransactionFlow()

        // val txIdGood = SecureHash("SHA256", "12345".toByteArray())
        val txIdBad = SecureHash( "SHA256", "Fail!".toByteArray())
        val ledgerService = mock<UtxoLedgerService>()
        whenever (ledgerService.findLedgerTransaction(txIdBad)).thenReturn(null)

        flow.marshallingService = jsonMarshallingService
        flow.ledgerService = ledgerService

        val badRequest = mock<RestRequestBody>()
        val body = FindTransactionParameters(txIdBad.toString())
        whenever(badRequest.getRequestBodyAs<FindTransactionParameters>(jsonMarshallingService)).thenReturn(body)

        val result = flow.call(badRequest)
        val resObj = jsonMarshallingService.parse<FindTransactionResponse>(result)
        Assertions.assertThat(resObj.transaction).isNull()
        Assertions.assertThat(resObj.errorMessage).isEqualTo("Failed to find transaction with id SHA256:4661696C21.")
    }

    @Test
    fun canReturnExampleFlowTransaction(){
        val flow = FindTransactionFlow()


        val txIdGood = SecureHash("SHA256", "12345".toByteArray())

        val keyGenerator = KeyPairGenerator.getInstance("EC")

        val participantKey = keyGenerator.generateKeyPair().public
        val testState = UtxoDemoFlow.TestUtxoState("text", listOf(participantKey), listOf("") )

        val ledgerTx = mock<UtxoLedgerTransaction>().apply {
            whenever(id).thenReturn(txIdGood)
            whenever(outputContractStates).thenReturn(listOf(testState))
            whenever(signatories).thenReturn(listOf(testState.participants.first()))
        }

        val ledgerService = mock<UtxoLedgerService>()
        whenever (ledgerService.findLedgerTransaction(txIdGood)).thenReturn(ledgerTx)

        flow.marshallingService = jsonMarshallingService
        flow.ledgerService = ledgerService

        val goodRequest = mock<RestRequestBody>()
        val body = FindTransactionParameters(txIdGood.toString())
        whenever(goodRequest.getRequestBodyAs<FindTransactionParameters>(jsonMarshallingService)).thenReturn(body)

        val result = flow.call(goodRequest)
        println(result)
        val resObj = jsonMarshallingService.parse<FindTransactionResponse>(result)
        Assertions.assertThat(resObj.transaction).isNotNull
        Assertions.assertThat(resObj.transaction!!.id).isEqualTo(txIdGood)
    }
}