package com.r3.corda.demo.utxo

import com.r3.corda.demo.utxo.contract.TestCommand
import com.r3.corda.demo.utxo.contract.TestUtxoState
import com.r3.corda.demo.utxo.contract.notaryverify.NotaryVerifyCommand
import com.r3.corda.demo.utxo.contract.notaryverify.NotaryVerifyState
import net.corda.v5.application.flows.ClientRequestBody
import net.corda.v5.application.flows.ClientStartableFlow
import net.corda.v5.application.flows.CordaInject
import net.corda.v5.application.flows.InitiatedBy
import net.corda.v5.application.flows.InitiatingFlow
import net.corda.v5.application.flows.ResponderFlow
import net.corda.v5.application.marshalling.JsonMarshallingService
import net.corda.v5.application.membership.MemberLookup
import net.corda.v5.application.messaging.FlowMessaging
import net.corda.v5.application.messaging.FlowSession
import net.corda.v5.base.annotations.Suspendable
import net.corda.v5.base.types.MemberX500Name
import net.corda.v5.ledger.common.NotaryLookup
import net.corda.v5.ledger.utxo.UtxoLedgerService
import org.slf4j.LoggerFactory
import java.time.Duration
import java.time.Instant

/**
 * Example flow to verify notary signature of another transaction in a state Contract.
 */

@InitiatingFlow(protocol = "utxo-verify-notary-flow-protocol")
class UtxoVerifyNotaryFlow : ClientStartableFlow {
    data class InputMessage(val input: String, val members: List<String>, val notary: String?)

    private companion object {
        val log = LoggerFactory.getLogger(this::class.java.enclosingClass)
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
    override fun call(requestBody: ClientRequestBody): String {
        log.info("UtxoVerifyNotarySignatureInContractFlow starting...")
        try {
            val request = requestBody.getRequestBodyAs(jsonMarshallingService, InputMessage::class.java)
            val myInfo = memberLookup.myInfo()
            val members = request.members.map { x500 ->
                requireNotNull(memberLookup.lookup(MemberX500Name.parse(x500))) {
                    "Member $x500 does not exist in the membership group"
                }
            }
            val testUtxoState = TestUtxoState(
                request.input,
                members.map { it.ledgerKeys.first() } + myInfo.ledgerKeys.first(),
                request.members + listOf(myInfo.name.toString())
            )

            val notary = if (request.notary == null) {
                notaryLookup.notaryServices.single()
            } else {
                requireNotNull(notaryLookup.lookup(MemberX500Name.parse(request.notary))) {
                    "Given notary ${request.notary} is invalid"
                }
            }
            val txBuilder = utxoLedgerService.createTransactionBuilder()

            val signedTransaction = txBuilder
                .setNotary(notary.name)
                .setTimeWindowBetween(Instant.now(), Instant.now().plusMillis(Duration.ofDays(1).toMillis()))
                .addOutputState(testUtxoState)
                .addCommand(TestCommand())
                .addSignatories(testUtxoState.participants)
                .toSignedTransaction()

            val sessions = members.map { flowMessaging.initiateFlow(it.name) }

            val finalizedTransaction = try {
                utxoLedgerService.finalize(
                    signedTransaction,
                    sessions
                ).transaction
            } catch (e: Exception) {
                log.warn("Finality failed", e)
                throw (e)
            }
            val notarySignature = finalizedTransaction.signatures.filter { it.proof != null }.first()
            val notaryVerifyState = NotaryVerifyState(
                finalizedTransaction.id,
                notarySignature,
                finalizedTransaction.notaryKey,
                testUtxoState.participants,
                testUtxoState.participantNames
            )

            val notaryVerifyTransaction = utxoLedgerService.createTransactionBuilder()
                .setNotary(notary.name)
                .setTimeWindowBetween(Instant.now(), Instant.now().plusMillis(Duration.ofDays(1).toMillis()))
                .addOutputState(notaryVerifyState)
                .addCommand(NotaryVerifyCommand())
                .addSignatories(testUtxoState.participants)
                .toSignedTransaction()

            return try {
                val finalizationResult = utxoLedgerService.finalize(
                    notaryVerifyTransaction,
                    sessions
                )
                finalizationResult.transaction.id.toString().also {
                    log.info("Success! Response: $it")
                }
            } catch (e: Exception) {
                log.warn("Finality failed", e)
                "Finality failed, ${e.message}"
            }
        } catch (e: Exception) {
            log.warn("Failed to process UtxoVerifyNotarySignatureInContractFlow for request body '$requestBody' because:'${e.message}'")
            throw e
        }
    }
}

@InitiatedBy(protocol = "utxo-verify-notary-flow-protocol")
class UtxoVerifyNotaryResponderFlow : ResponderFlow {

    private companion object {
        val log = LoggerFactory.getLogger(this::class.java.enclosingClass)
    }

    @CordaInject
    lateinit var utxoLedgerService: UtxoLedgerService

    @Suspendable
    override fun call(session: FlowSession) {
        try {
            utxoLedgerService.receiveFinality(session) { ledgerTransaction ->
                log.info("Verify: Accepted base transaction - ${ledgerTransaction.id}")
            }
            val finalizationResult = utxoLedgerService.receiveFinality(session) { ledgerTransaction ->
                log.info("Verify: Accepted notary verify transaction - ${ledgerTransaction.id}")
            }
            log.info("Finished UtxoVerifyNotarySignatureInContractFlow - ${finalizationResult.transaction.id}")
        } catch (e: Exception) {
            log.warn("Exceptionally finished responder flow", e)
        }
    }
}
