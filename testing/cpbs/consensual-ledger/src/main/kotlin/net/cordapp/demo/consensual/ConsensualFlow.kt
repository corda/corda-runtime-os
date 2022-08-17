package net.cordapp.demo.consensual

import net.corda.v5.application.flows.CordaInject
import net.corda.v5.application.flows.FlowEngine
import net.corda.v5.application.flows.RPCRequestData
import net.corda.v5.application.flows.RPCStartableFlow
import net.corda.v5.application.marshalling.JsonMarshallingService
import net.corda.v5.base.annotations.Suspendable
import net.corda.v5.base.types.MemberX500Name
import net.corda.v5.base.util.contextLogger
import net.corda.v5.ledger.consensual.ConsensualLedgerService
import net.corda.v5.ledger.consensual.ConsensualState
import net.corda.v5.ledger.consensual.Party
import net.corda.v5.ledger.consensual.transaction.ConsensualLedgerTransaction
import java.security.KeyPairGenerator
import java.security.PublicKey
import java.time.Instant

/**
 * Example consensual flow. Currently, does almost nothing other than verify that
 * we can inject the ledger service. Eventually it should do a two-party IOUState
 * agreement.
 */

class TestPartyImpl(override val name: MemberX500Name, override val owningKey: PublicKey) : Party

class ConsensualFlow : RPCStartableFlow {
    data class InputMessage(val number: Int)
    data class ResultMessage(val text: String)

    class TestConsensualState(
        val testField: String,
        override val participants: List<Party> /// todo check serialization error
    ) : ConsensualState {
        override fun verify(ledgerTransaction: ConsensualLedgerTransaction): Boolean = true
    }

    private companion object {
        val log = contextLogger()
    }

    @CordaInject
    lateinit var flowEngine: FlowEngine

    @CordaInject
    lateinit var consensualLedgerService: ConsensualLedgerService

    @CordaInject
    lateinit var jsonMarshallingService: JsonMarshallingService

    @Suspendable
    override fun call(requestBody: RPCRequestData): String {
        log.info("Consensual flow demo starting...")
        try {
            val kpg = KeyPairGenerator.getInstance("RSA")
            kpg.initialize(512) // Shortest possible to not slow down tests.
            val testPublicKey = kpg.genKeyPair().public

            val testMemberX500Name = MemberX500Name("R3", "London", "GB")

            val testConsensualState =
                TestConsensualState(
                    "test",
                    listOf(
                        TestPartyImpl(
                            testMemberX500Name,
                            testPublicKey
                        )
                    )
                )

            val txBuilder = consensualLedgerService.getTransactionBuilder()
            val signedTransaction = txBuilder
                .withTimestamp(Instant.now())
                .withStates(testConsensualState)
                .signInitial(testPublicKey)

            val resultMessage = ResultMessage(text = signedTransaction.toString())
            log.info("Success! Response: $resultMessage")
            return jsonMarshallingService.format(resultMessage)
        } catch (e: Exception) {
            log.warn("Failed to process consensual flow for request body '$requestBody' because:'${e.message}'")
            throw e
        }
    }
}
