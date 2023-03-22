package net.cordacon.example.landregistry.flows

import net.corda.v5.application.flows.InitiatingFlow
import net.corda.v5.application.flows.ClientStartableFlow
import net.corda.v5.application.flows.CordaInject
import net.corda.v5.application.flows.ClientRequestBody
import net.cordacon.example.landregistry.states.LandTitleState
import net.corda.v5.application.marshalling.JsonMarshallingService
import net.corda.v5.application.membership.MemberLookup
import net.corda.v5.base.annotations.Suspendable
import net.corda.v5.base.types.MemberX500Name
import net.corda.v5.ledger.utxo.UtxoLedgerService
import java.time.format.DateTimeFormatter

/**
 * A flow to fetch unconsumed land titles. To fetch a specific land title use the title number as a filter
 */
@InitiatingFlow(protocol = "fetch-land-title")
class FetchLandTitleFlow : ClientStartableFlow {

    @CordaInject
    lateinit var utxoLedgerService: UtxoLedgerService

    @CordaInject
    lateinit var jsonMarshallingService: JsonMarshallingService

    @CordaInject
    lateinit var memberLookup: MemberLookup

    @Suspendable
    override fun call(requestBody: ClientRequestBody): String {
        val formatter: DateTimeFormatter = DateTimeFormatter.ofPattern("dd-MM-yyyy HH:mm:ss")
        val request = requestBody.getRequestBodyAs(jsonMarshallingService, Filter::class.java)
        val landTitleList : List<LandTitle>

        if(request.titleNumber.trim() != ""){
            val stateAndRefs = utxoLedgerService.findUnconsumedStatesByType(LandTitleState::class.java)

            landTitleList = stateAndRefs.filter { it.state.contractState.titleNumber == request.titleNumber }
                .map {
                    LandTitle(
                        it.state.contractState.titleNumber,
                        it.state.contractState.location,
                        it.state.contractState.areaInSquareMeter.toString().plus(" sqmt"),
                        it.state.contractState.extraDetails,
                        formatter.format(it.state.contractState.registrationTimeStamp),
                        memberLookup.lookup(it.state.contractState.owner)!!.name,
                        memberLookup.lookup(it.state.contractState.issuer)!!.name
                    )
                }
        }else{
            val stateAndRefList = utxoLedgerService.findUnconsumedStatesByType(LandTitleState::class.java)
            landTitleList = stateAndRefList.map {
                    LandTitle(
                        it.state.contractState.titleNumber,
                        it.state.contractState.location,
                        it.state.contractState.areaInSquareMeter.toString().plus(" sqmt"),
                        it.state.contractState.extraDetails,
                        formatter.format(it.state.contractState.registrationTimeStamp),
                        memberLookup.lookup(it.state.contractState.owner)!!.name,
                        memberLookup.lookup(it.state.contractState.issuer)!!.name,
                    )
                }
            }
        return jsonMarshallingService.format(landTitleList)
    }
}

data class Filter(
    val titleNumber: String = ""
)

data class LandTitle(
    val titleNumber: String,
    val location: String,
    val area: String,
    val extraDetails: String,
    val registrationTitleStamp: String,
    val owner: MemberX500Name,
    val issuer: MemberX500Name
)