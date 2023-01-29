package net.cordacon.example.landregistry.flows

import net.corda.v5.application.flows.InitiatingFlow
import net.corda.v5.application.flows.ClientStartableFlow
import net.corda.v5.application.flows.CordaInject
import net.corda.v5.application.flows.getRequestBodyAs
import net.corda.v5.application.flows.RestRequestBody
import net.cordacon.example.landregistry.states.LandTitleState
import net.corda.v5.application.marshalling.JsonMarshallingService
import net.corda.v5.application.membership.MemberLookup
import net.corda.v5.base.annotations.Suspendable
import net.corda.v5.base.types.MemberX500Name
import net.corda.v5.base.util.detailedLogger
import net.corda.v5.ledger.utxo.UtxoLedgerService
import java.time.format.DateTimeFormatter

/**
 * A flow to fetch unconsumed land titles. To fetch a specific land title use the title number as a filter
 */
@InitiatingFlow("fetch-land-title")
class FetchLandTitleFlow : ClientStartableFlow {

    private companion object {
        val log = detailedLogger()
    }

    @CordaInject
    lateinit var utxoLedgerService: UtxoLedgerService

    @CordaInject
    lateinit var jsonMarshallingService: JsonMarshallingService

    @CordaInject
    lateinit var memberLookup: MemberLookup

    @Suspendable
    override fun call(requestBody: RestRequestBody): String {
        val formatter: DateTimeFormatter = DateTimeFormatter.ofPattern("dd-MM-yyyy HH:mm:ss")
        val request = requestBody.getRequestBodyAs<Filter>(jsonMarshallingService)
        val stateAndRefList = utxoLedgerService.findUnconsumedStatesByType(LandTitleState::class.java)
        val landTitleList : ArrayList<LandTitle> = ArrayList()

        if(request.titleNumber.trim() != ""){
            val stateAndRef = utxoLedgerService.findUnconsumedStatesByType(LandTitleState::class.java).firstOrNull {
                it.state.contractState.titleNumber == request.titleNumber
            }
            if(stateAndRef!=null){
                val landTitle = LandTitle(
                    stateAndRef.state.contractState.titleNumber,
                    stateAndRef.state.contractState.location,
                    stateAndRef.state.contractState.areaInSquareMeter.toString().plus(" sqmt"),
                    stateAndRef.state.contractState.extraDetails,
                    formatter.format(stateAndRef.state.contractState.registrationTitleStamp),
                    memberLookup.lookup(stateAndRef.state.contractState.owner)!!.name,
                    memberLookup.lookup(stateAndRef.state.contractState.issuer)!!.name,
                )
                landTitleList.add(landTitle)
            }
        }else{
            stateAndRefList.forEach {
                val landTitle = LandTitle(
                    it.state.contractState.titleNumber,
                    it.state.contractState.location,
                    it.state.contractState.areaInSquareMeter.toString().plus(" sqmt"),
                    it.state.contractState.extraDetails,
                    formatter.format(it.state.contractState.registrationTitleStamp),
                    memberLookup.lookup(it.state.contractState.owner)!!.name,
                    memberLookup.lookup(it.state.contractState.issuer)!!.name,
                )
                landTitleList.add(landTitle)
            }
        }

        return jsonMarshallingService.format(landTitleList)
    }

}

data class Filter(
    val titleNumber: String
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