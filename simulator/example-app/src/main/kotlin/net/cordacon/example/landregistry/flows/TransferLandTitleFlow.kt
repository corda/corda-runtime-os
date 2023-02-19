package net.cordacon.example.landregistry.flows

import net.corda.v5.application.flows.InitiatingFlow
import net.corda.v5.application.flows.ClientStartableFlow
import net.corda.v5.application.flows.CordaInject
import net.corda.v5.application.flows.RestRequestBody
import net.corda.v5.application.flows.InitiatedBy
import net.corda.v5.application.flows.ResponderFlow
import net.corda.v5.application.flows.getRequestBodyAs
import net.cordacon.example.landregistry.states.LandTitleContract
import net.cordacon.example.landregistry.states.LandTitleState
import net.corda.v5.application.marshalling.JsonMarshallingService
import net.corda.v5.application.membership.MemberLookup
import net.corda.v5.application.messaging.FlowMessaging
import net.corda.v5.application.messaging.FlowSession
import net.corda.v5.base.annotations.Suspendable
import net.corda.v5.base.types.MemberX500Name
import net.corda.v5.base.util.days
import net.corda.v5.base.util.detailedLogger
import net.corda.v5.ledger.utxo.UtxoLedgerService
import java.time.Instant
import java.time.LocalDateTime

/**
 * A flow to transfer land title from one owner to another.
 */
@InitiatingFlow(protocol = "transfer-title")
class TransferLandTitleFlow : ClientStartableFlow {

    private companion object {
        val log = detailedLogger()
    }

    @CordaInject
    lateinit var jsonMarshallingService: JsonMarshallingService

    @CordaInject
    lateinit var memberLookup: MemberLookup

    @CordaInject
    lateinit var utxoLedgerService: UtxoLedgerService

    @CordaInject
    lateinit var flowMessaging: FlowMessaging

    @Suspendable
    override fun call(requestBody: RestRequestBody): String {
        val request = requestBody.getRequestBodyAs<TransferLandTitleRequest>(jsonMarshallingService)
        val owner = memberLookup.lookup(request.owner)
            ?: throw IllegalArgumentException("Unknown holder: ${request.owner}.")

        val oldStateAndRef = utxoLedgerService.findUnconsumedStatesByType(LandTitleState::class.java).firstOrNull {
            it.state.contractState.titleNumber == request.titleNumber
        } ?: throw java.lang.IllegalArgumentException("Title Number: ${request.titleNumber} does not exist.")

        val oldState = oldStateAndRef.state.contractState

        val landTitleState = LandTitleState(
            oldState.titleNumber,
            oldState.location,
            oldState.areaInSquareMeter,
            oldState.extraDetails,
            LocalDateTime.now(),
            owner.ledgerKeys.first(),
            oldState.issuer
        )

        val transaction = utxoLedgerService
            .getTransactionBuilder()
            .setTimeWindowBetween(Instant.now(), Instant.now().plusMillis(1.days.toMillis()))
            .setNotary(oldStateAndRef.state.notary)
            .addInputState(oldStateAndRef.ref)
            .addOutputState(landTitleState)
            .addCommand(LandTitleContract.Transfer)
            .addSignatories(listOf(landTitleState.issuer, landTitleState.owner, oldState.owner))

        val partiallySignedTransaction = transaction.toSignedTransaction()

        val issuer = memberLookup.lookup(oldState.issuer)
            ?: throw IllegalArgumentException("Unknown Issuer: ${oldState.issuer}.")

        val issuerSession = flowMessaging.initiateFlow(issuer.name)
        val ownerSession = flowMessaging.initiateFlow(request.owner)

        // CP Signing automatically handled by finalize()
        val finalizedSignedTransaction = utxoLedgerService.finalize(
            partiallySignedTransaction,
            listOf(issuerSession, ownerSession)
        )

        return finalizedSignedTransaction.id.toString()

    }
}

@InitiatedBy(protocol = "transfer-title")
class TransferLandTitleResponderFlow: ResponderFlow {

    private companion object {
        val log = detailedLogger()
    }

    @CordaInject
    lateinit var utxoLedgerService: UtxoLedgerService

    @Suspendable
    override fun call(session: FlowSession) {
        utxoLedgerService.receiveFinality(session) {}
    }
}

data class TransferLandTitleRequest(
    val titleNumber: String,
    val owner: MemberX500Name
)