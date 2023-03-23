package net.cordacon.example.landregistry.flows

import net.corda.v5.application.flows.ClientRequestBody
import net.corda.v5.application.flows.InitiatingFlow
import net.corda.v5.application.flows.ClientStartableFlow
import net.corda.v5.application.flows.CordaInject
import net.corda.v5.application.flows.InitiatedBy
import net.corda.v5.application.flows.ResponderFlow
import net.cordacon.example.landregistry.states.LandTitleContract
import net.cordacon.example.landregistry.states.LandTitleState
import net.corda.v5.application.marshalling.JsonMarshallingService
import net.corda.v5.application.membership.MemberLookup
import net.corda.v5.application.messaging.FlowMessaging
import net.corda.v5.application.messaging.FlowSession
import net.corda.v5.base.annotations.Suspendable
import net.corda.v5.base.exceptions.CordaRuntimeException
import net.corda.v5.base.types.MemberX500Name
import net.corda.v5.ledger.utxo.UtxoLedgerService
import java.time.Instant
import java.time.LocalDateTime
import kotlin.time.Duration.Companion.days

/**
 * A flow to transfer land title from one owner to another.
 */
@InitiatingFlow(protocol = "transfer-title")
class TransferLandTitleFlow : ClientStartableFlow {

    @CordaInject
    lateinit var jsonMarshallingService: JsonMarshallingService

    @CordaInject
    lateinit var memberLookup: MemberLookup

    @CordaInject
    lateinit var utxoLedgerService: UtxoLedgerService

    @CordaInject
    lateinit var flowMessaging: FlowMessaging

    @Suspendable
    override fun call(requestBody: ClientRequestBody): String {
        val request = requestBody.getRequestBodyAs(jsonMarshallingService, TransferLandTitleRequest::class.java)
        val newOwner = memberLookup.lookup(request.newOwner)
            ?: throw CordaRuntimeException("Unknown holder: ${request.newOwner}.")

        val oldStateAndRefs = utxoLedgerService.findUnconsumedStatesByType(LandTitleState::class.java).filter {
            it.state.contractState.titleNumber == request.titleNumber
        }

        if(oldStateAndRefs.isEmpty()){
            throw CordaRuntimeException("Land Title with title nuumber ${request.titleNumber} does not exist")
        }
        if(oldStateAndRefs.size > 1){
            throw CordaRuntimeException("Multiple Land State Found with title number: ${request.titleNumber}")
        }

        val oldStateAndRef = oldStateAndRefs[0]
        val oldState = oldStateAndRef.state.contractState

        val landTitleState = oldState.copy(
            owner = newOwner.ledgerKeys.first(),
            registrationTimeStamp = LocalDateTime.now()
        )

        val transaction = utxoLedgerService
            .transactionBuilder
            .setTimeWindowBetween(Instant.now(), Instant.now().plusMillis(1.days.inWholeMilliseconds))
            .setNotary(oldStateAndRef.state.notaryName)
            .addInputState(oldStateAndRef.ref)
            .addOutputState(landTitleState)
            .addCommand(LandTitleContract.Transfer)
            .addSignatories(listOf(landTitleState.issuer, landTitleState.owner, oldState.owner))

        val partiallySignedTransaction = transaction.toSignedTransaction()

        val issuer = memberLookup.lookup(oldState.issuer)
            ?: throw IllegalArgumentException("Unknown Issuer: ${oldState.issuer}.")

        val issuerSession = flowMessaging.initiateFlow(issuer.name)
        val ownerSession = flowMessaging.initiateFlow(request.newOwner)

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

    @CordaInject
    lateinit var utxoLedgerService: UtxoLedgerService

    @Suspendable
    override fun call(session: FlowSession) {
        utxoLedgerService.receiveFinality(session) {}
    }
}

data class TransferLandTitleRequest(
    val titleNumber: String,
    val newOwner: MemberX500Name
)