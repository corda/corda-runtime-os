package net.cordapp.demo.utxo

import net.corda.application.impl.services.json.JsonMarshallingServiceImpl
import net.corda.crypto.core.SecureHashImpl
import net.corda.crypto.core.parseSecureHash
import net.corda.v5.application.crypto.DigestService
import net.corda.v5.application.flows.ClientRequestBody
import net.corda.v5.ledger.utxo.StateAndRef
import net.corda.v5.ledger.utxo.StateRef
import net.corda.v5.ledger.utxo.TransactionState
import net.corda.v5.ledger.utxo.UtxoLedgerService
import net.corda.v5.ledger.utxo.transaction.UtxoLedgerTransaction
import net.cordapp.demo.utxo.contract.TestUtxoState
import org.assertj.core.api.Assertions
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import java.security.KeyPairGenerator

class TestPeekTransactionFlow {
        private val jsonMarshallingService = JsonMarshallingServiceImpl()

        private lateinit var digestService: DigestService

        @BeforeEach
        fun setUp() {
            digestService = mock<DigestService>().also {
                val secureHashStringCaptor = argumentCaptor<String>()
                whenever(it.parseSecureHash(secureHashStringCaptor.capture())).thenAnswer {
                    val secureHashString = secureHashStringCaptor.firstValue
                    parseSecureHash(secureHashString)
                }
            }
        }

        @Test
        fun missingTransactionReturnsError() {
            val flow = PeekTransactionFlow()

            val txIdBad = SecureHashImpl("SHA256", "Fail!".toByteArray())
            val ledgerService = mock<UtxoLedgerService>()
            whenever(ledgerService.findLedgerTransaction(txIdBad)).thenReturn(null)

            flow.marshallingService = jsonMarshallingService
            flow.ledgerService = ledgerService
            flow.digestService = digestService

            val badRequest = mock<ClientRequestBody>()
            val body = PeekTransactionParameters(txIdBad.toString())
            whenever(badRequest.getRequestBodyAs(jsonMarshallingService, PeekTransactionParameters::class.java)).thenReturn(body)

            val result = flow.call(badRequest)
            val resObj = jsonMarshallingService.parse(result, PeekTransactionResponse::class.java)
            Assertions.assertThat(resObj.inputs).isEmpty()
            Assertions.assertThat(resObj.outputs).isEmpty()
            Assertions.assertThat(resObj.errorMessage)
                .isEqualTo("Failed to load transaction.")
        }

        @Test
        fun canReturnExampleFlowStates() {
            val flow = PeekTransactionFlow()


            val txIdGood = SecureHashImpl("SHA256", "12345".toByteArray())

            val keyGenerator = KeyPairGenerator.getInstance("EC")

            val participantKey = keyGenerator.generateKeyPair().public
            val testState = TestUtxoState("text", listOf(participantKey), listOf(""))
            val testContractState = mock<TransactionState<TestUtxoState>>().apply {
                whenever(this.contractState).thenReturn(testState)
            }

            val testStateAndRef = mock<StateAndRef<TestUtxoState>>().apply {
                whenever(ref).thenReturn(StateRef(txIdGood, 0))
                whenever(state).thenReturn(testContractState )
            }


            val ledgerTx = mock<UtxoLedgerTransaction>().apply {
                whenever(id).thenReturn(txIdGood)
                whenever(getOutputStateAndRefs(TestUtxoState::class.java))
                    .thenReturn(listOf(testStateAndRef))
                whenever(signatories).thenReturn(listOf(testState.participants.first()))
            }

            val ledgerService = mock<UtxoLedgerService>()
            whenever(ledgerService.findLedgerTransaction(txIdGood)).thenReturn(ledgerTx)

            flow.marshallingService = jsonMarshallingService
            flow.ledgerService = ledgerService
            flow.digestService = digestService

            val goodRequest = mock<ClientRequestBody>()
            val body = PeekTransactionParameters(txIdGood.toString())
            whenever(goodRequest.getRequestBodyAs(jsonMarshallingService, PeekTransactionParameters::class.java)).thenReturn(body)

            val result = flow.call(goodRequest)
            val resObj = jsonMarshallingService.parse(result, PeekTransactionResponse::class.java)
            Assertions.assertThat(resObj.inputs).isEmpty()
            Assertions.assertThat(resObj.outputs).hasSize(1)
            Assertions.assertThat(resObj.outputs.first().testField).isEqualTo("text")
            Assertions.assertThat(resObj.errorMessage).isNull()
        }
}