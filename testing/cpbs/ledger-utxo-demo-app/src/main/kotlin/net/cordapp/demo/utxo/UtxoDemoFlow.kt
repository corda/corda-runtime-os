package net.cordapp.demo.utxo

import net.corda.v5.application.flows.CordaInject
import net.corda.v5.application.flows.InitiatedBy
import net.corda.v5.application.flows.InitiatingFlow
import net.corda.v5.application.flows.RPCRequestData
import net.corda.v5.application.flows.RPCStartableFlow
import net.corda.v5.application.flows.ResponderFlow
import net.corda.v5.application.flows.getRequestBodyAs
import net.corda.v5.application.marshalling.JsonMarshallingService
import net.corda.v5.application.membership.MemberLookup
import net.corda.v5.application.messaging.FlowMessaging
import net.corda.v5.application.messaging.FlowSession
import net.corda.v5.base.annotations.Suspendable
import net.corda.v5.base.types.MemberX500Name
import net.corda.v5.base.util.contextLogger
import net.corda.v5.base.util.days
import net.corda.v5.ledger.common.NotaryLookup
import net.corda.v5.ledger.common.Party
import net.corda.v5.ledger.utxo.BelongsToContract
import net.corda.v5.ledger.utxo.Command
import net.corda.v5.ledger.utxo.Contract
import net.corda.v5.ledger.utxo.ContractState
import net.corda.v5.ledger.utxo.UtxoLedgerService
import net.corda.v5.ledger.utxo.transaction.UtxoLedgerTransaction
import java.security.PublicKey
import java.time.Instant

/**
 * Example utxo flow.
 * TODO expand description
 */

@InitiatingFlow("utxo-flow-protocol")
class UtxoDemoFlow : RPCStartableFlow {
    data class InputMessage(val input: String, val members: List<String>, val notary: String)

    class TestContract : Contract {
        override fun verify(transaction: UtxoLedgerTransaction) {
        }
    }

    @BelongsToContract(TestContract::class)
    class TestUtxoState(
        val testField: String,
        override val participants: List<PublicKey>
    ) : ContractState

    class TestCommand : Command

    private companion object {
        val log = contextLogger()
    }

    @CordaInject
    lateinit var flowMessaging: FlowMessaging

    @CordaInject
    lateinit var utxoLedgerService: UtxoLedgerService

    @CordaInject
    lateinit var jsonMarshallingService: JsonMarshallingService

    @CordaInject
    lateinit var memberLookup: MemberLookup

    @CordaInject
    lateinit var notaryLookup: NotaryLookup

    @Suspendable
    override fun call(requestBody: RPCRequestData): String {
        log.info("Utxo flow demo starting...")
        try {
            val request = requestBody.getRequestBodyAs<InputMessage>(jsonMarshallingService)

            val myInfo = memberLookup.myInfo()
            val members = request.members.map { x500 ->
                requireNotNull(memberLookup.lookup(MemberX500Name.parse(x500))) {
                    "Member $x500 does not exist in the membership group"
                }
            }
            val testUtxoState = TestUtxoState(
                request.input,
                members.map { it.ledgerKeys.first() } + myInfo.ledgerKeys.first()
            )

            val notary = notaryLookup.notaryServices.single()
            val notaryKey = memberLookup.lookup().single {
                it.memberProvidedContext["corda.notary.service.name"] == notary.name.toString()
            }.ledgerKeys.first()
            // TODO CORE-6173 use proper notary key
            //val notaryKey = notary.publicKey

            val txBuilder = utxoLedgerService.getTransactionBuilder()

            @Suppress("DEPRECATION")
            val signedTransaction = txBuilder
                .setNotary(Party(notary.name, notaryKey))
                .setTimeWindowBetween(Instant.now(), Instant.now().plusMillis(1.days.toMillis()))
                .addOutputState(testUtxoState)
                .addCommand(TestCommand())
                .addSignatories(testUtxoState.participants)
                .toSignedTransaction(myInfo.ledgerKeys.first())

            val sessions = members.map { flowMessaging.initiateFlow(it.name) }

            return try {
                val finalizedSignedTransaction = utxoLedgerService.finalize(
                    signedTransaction,
                    sessions
                )
                finalizedSignedTransaction.id.toString().also {
                    log.info("Success! Response: $it")
                }

            } catch (e: Exception) {
                log.warn("Finality failed", e)
                "Finality failed, ${e.message}"
            }
        } catch (e: Exception) {
            log.warn("Failed to process utxo flow for request body '$requestBody' because:'${e.message}'")
            throw e
        }
    }
}

@InitiatedBy("utxo-flow-protocol")
class UtxoResponderFlow : ResponderFlow {

    private companion object {
        val log = contextLogger()
    }

    @CordaInject
    lateinit var utxoLedgerService: UtxoLedgerService

    @Suspendable
    override fun call(session: FlowSession) {
        try {
            val finalizedSignedTransaction = utxoLedgerService.receiveFinality(session) { ledgerTransaction ->
                val state = ledgerTransaction.outputContractStates.first() as UtxoDemoFlow.TestUtxoState
                if (state.testField == "fail") {
                    log.info("Failed to verify the transaction - ${ledgerTransaction.id}")
                    throw IllegalStateException("Failed verification")
                }
                log.info("Verified the transaction- ${ledgerTransaction.id}")
            }
            log.info("Finished responder flow - ${finalizedSignedTransaction.id}")
        } catch (e: Exception) {
            log.warn("Exceptionally finished responder flow", e)
        }
    }
}
