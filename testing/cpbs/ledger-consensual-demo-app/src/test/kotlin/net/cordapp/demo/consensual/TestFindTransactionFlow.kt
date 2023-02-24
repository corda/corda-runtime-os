package net.cordapp.demo.consensual

import net.corda.application.impl.services.json.JsonMarshallingServiceImpl
import net.corda.v5.application.flows.ClientRequestBody
import net.corda.v5.application.marshalling.JsonMarshallingService
import net.corda.v5.crypto.SecureHash
import net.corda.v5.ledger.consensual.ConsensualLedgerService
import net.corda.v5.ledger.consensual.transaction.ConsensualLedgerTransaction
import net.cordapp.demo.consensual.contract.TestConsensualState
import org.assertj.core.api.Assertions
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import java.security.KeyPairGenerator

class TestFindTransactionFlow {
    val marshallingService: JsonMarshallingService = JsonMarshallingServiceImpl()

    @Test
    fun missingTransactionReturnsNull(){
        val flow = FindTransactionFlow()

        // val txIdGood = SecureHash("SHA256", "12345".toByteArray())
        val txIdBad = SecureHash( "SHA256", "Fail!".toByteArray())
        val ledgerService = mock<ConsensualLedgerService>()
        whenever (ledgerService.findLedgerTransaction(txIdBad)).thenReturn(null)

        flow.marshallingService = marshallingService
        flow.ledgerService = ledgerService

        val badRequest = mock<ClientRequestBody>()
        val body = FindTransactionParameters(txIdBad.toString())
        whenever(badRequest.getRequestBodyAs(marshallingService, FindTransactionParameters::class.java)).thenReturn(body)

        val result = flow.call(badRequest)
        val resObj = marshallingService.parse(result, FindTransactionResponse::class.java)
        Assertions.assertThat(resObj.transaction).isNull()
        Assertions.assertThat(resObj.errorMessage).isEqualTo("Failed to find transaction with id SHA256:4661696C21.")
    }

    @Test
    fun canReturnExampleFlowTransaction(){
        val flow = FindTransactionFlow()


        val txIdGood = SecureHash("SHA256", "12345".toByteArray())

        val keyGenerator = KeyPairGenerator.getInstance("EC")

        val participantKey = keyGenerator.generateKeyPair().public
        val testState = TestConsensualState("text", listOf(participantKey) )

        val ledgerTx = mock<ConsensualLedgerTransaction>().apply {
            whenever(id).thenReturn(txIdGood)
            whenever(states).thenReturn(listOf(testState))
            whenever(requiredSignatories).thenReturn(setOf(testState.participants.first()))
        }

        val ledgerService = mock<ConsensualLedgerService>()
        whenever (ledgerService.findLedgerTransaction(txIdGood)).thenReturn(ledgerTx)

        flow.marshallingService = marshallingService
        flow.ledgerService = ledgerService

        val goodRequest = mock<ClientRequestBody>()
        val body = FindTransactionParameters(txIdGood.toString())
        whenever(goodRequest.getRequestBodyAs(marshallingService, FindTransactionParameters::class.java)).thenReturn(body)

        val result = flow.call(goodRequest)
        println(result)
        val resObj = marshallingService.parse(result, FindTransactionResponse::class.java)
        Assertions.assertThat(resObj.transaction).isNotNull
        Assertions.assertThat(resObj.transaction!!.id).isEqualTo(txIdGood)
    }
}