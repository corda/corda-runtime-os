package net.cordacon.example.landregistry.flows

import net.corda.v5.application.flows.ClientRequestBody
import net.corda.v5.application.flows.InitiatingFlow
import net.corda.v5.application.flows.ClientStartableFlow
import net.corda.v5.application.flows.CordaInject
import net.corda.v5.application.flows.ResponderFlow
import net.corda.v5.application.flows.InitiatedBy
import net.cordacon.example.landregistry.states.LandTitleContract
import net.cordacon.example.landregistry.states.LandTitleState
import net.corda.v5.application.marshalling.JsonMarshallingService
import net.corda.v5.application.membership.MemberLookup
import net.corda.v5.application.messaging.FlowMessaging
import net.corda.v5.application.messaging.FlowSession
import net.corda.v5.base.annotations.Suspendable
import net.corda.v5.base.exceptions.CordaRuntimeException
import net.corda.v5.base.types.MemberX500Name
import net.corda.v5.ledger.common.NotaryLookup
import net.corda.v5.ledger.common.Party
import net.corda.v5.ledger.utxo.UtxoLedgerService
import java.time.Instant
import java.time.LocalDateTime
import kotlin.time.Duration.Companion.days

/**
 * A flow to issue land title
 */
@InitiatingFlow(protocol = "issue-title")
class IssueLandTitleFlow: ClientStartableFlow {



    @CordaInject
    lateinit var jsonMarshallingService: JsonMarshallingService

    @CordaInject
    lateinit var utxoLedgerService: UtxoLedgerService

    @CordaInject
    lateinit var flowMessaging: FlowMessaging

    @CordaInject
    lateinit var memberLookup: MemberLookup

    @CordaInject
    lateinit var notaryLookup: NotaryLookup

    @Suspendable
    override fun call(requestBody: ClientRequestBody): String {
        val request = requestBody.getRequestBodyAs(jsonMarshallingService, LandRegistryRequest::class.java)

        val exists = utxoLedgerService.findUnconsumedStatesByType(LandTitleState::class.java).any {
            it.state.contractState.titleNumber == request.titleNumber
        }
        if(exists)
            throw CordaRuntimeException("Title Number: ${request.titleNumber} already exist.")

        val myInfo = memberLookup.myInfo()
        val owner = memberLookup.lookup(request.owner)
            ?: throw IllegalArgumentException("Unknown holder: ${request.owner}.")

        val landTitleState = LandTitleState(
            request.titleNumber,
            request.location,
            request.areaInSquareMeter,
            request.extraDetails,
            LocalDateTime.now(),
            owner.ledgerKeys.first(),
            myInfo.ledgerKeys.first()
        )

        // CORE-6173 Cannot use proper notary key
        val notary = notaryLookup.notaryServices.first()
        val notaryKey = memberLookup.lookup().first {
            it.memberProvidedContext["corda.notary.service.name"] == notary.name.toString()
        }.ledgerKeys.first()

        // Setting time-window is mandatory
        val transaction = utxoLedgerService
            .transactionBuilder
            .setTimeWindowBetween(Instant.now(), Instant.now().plusMillis(1.days.inWholeMilliseconds))
            .setNotary(Party(notary.name, notaryKey))
            .addOutputState(landTitleState)
            .addCommand(LandTitleContract.Issue)
            .addSignatories(listOf(landTitleState.issuer))

        val signedTransaction = transaction.toSignedTransaction()
        val flowSession = flowMessaging.initiateFlow(owner.name)
        val finalizedSignedTransaction = utxoLedgerService.finalize(
            signedTransaction, listOf(flowSession)
        )
        return finalizedSignedTransaction.id.toString()
    }
}

@InitiatedBy(protocol = "issue-title")
class IssueLandTitleResponderFlow: ResponderFlow {

    @CordaInject
    lateinit var utxoLedgerService: UtxoLedgerService

    @Suspendable
    override fun call(session: FlowSession) {
        utxoLedgerService.receiveFinality(session) {
            if(it.outputContractStates.size !=1)
                throw CordaRuntimeException("Failed verification - transaction did not have exactly one output")

            val landTitleState = it.outputContractStates[0] as LandTitleState
            if(!(landTitleState.location.contains("Mumbai"))){
                throw CordaRuntimeException("Failed verification - Land should be located in Mumbai")
            }
        }
    }
}

data class LandRegistryRequest(
    val titleNumber: String,
    val location: String,
    val areaInSquareMeter: Int,
    val extraDetails: String,
    val owner: MemberX500Name
)
