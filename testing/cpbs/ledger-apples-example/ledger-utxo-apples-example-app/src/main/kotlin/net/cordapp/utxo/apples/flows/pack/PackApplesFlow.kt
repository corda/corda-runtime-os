package net.cordapp.utxo.apples.flows.pack

import net.corda.v5.application.flows.ClientRequestBody
import net.corda.v5.application.flows.ClientStartableFlow
import net.corda.v5.application.flows.CordaInject
import net.corda.v5.application.marshalling.JsonMarshallingService
import net.corda.v5.application.membership.MemberLookup
import net.corda.v5.base.annotations.Suspendable
import net.corda.v5.ledger.common.NotaryLookup
import net.corda.v5.ledger.common.Party
import net.corda.v5.ledger.utxo.UtxoLedgerService
import net.cordapp.utxo.apples.states.BasketOfApples
import net.cordapp.utxo.apples.contracts.BasketOfApplesContract
import java.time.Instant
import java.time.temporal.ChronoUnit

class PackApplesFlow : ClientStartableFlow {

    @CordaInject
    lateinit var jsonMarshallingService: JsonMarshallingService

    @CordaInject
    lateinit var memberLookup: MemberLookup

    @CordaInject
    lateinit var notaryLookup: NotaryLookup

    @CordaInject
    lateinit var utxoLedgerService: UtxoLedgerService

    @Suspendable
    override fun call(requestBody: ClientRequestBody): String {
        val request = requestBody.getRequestBodyAs(jsonMarshallingService, PackApplesRequest::class.java)
        val appleDescription = request.appleDescription
        val weight = request.weight

        // Retrieve the notaries public key (this will change)
        val notaryInfo = notaryLookup.notaryServices.single()
        val notaryKey = memberLookup.lookup().single {
            it.memberProvidedContext["corda.notary.service.name"] == notaryInfo.name.toString()
        }.ledgerKeys.first()
        val notary = Party(notaryInfo.name, notaryKey)

        //val myInfo = memberLookup.myInfo()
        //val ourIdentity = Party(myInfo.name, myInfo.ledgerKeys.first())
        val ourIdentity = memberLookup.myInfo().let { Party(it.name, it.ledgerKeys.first()) }

        // Building the output BasketOfApples state
        val basket = BasketOfApples(
            description = appleDescription,
            farm = ourIdentity,
            owner = ourIdentity,
            weight = weight,
            participants = listOf(ourIdentity.owningKey)
        )

        // Create the transaction
        @Suppress("DEPRECATION")
        val transaction = utxoLedgerService.getTransactionBuilder()
            .setNotary(notary)
            .addOutputState(basket)
            .addCommand(BasketOfApplesContract.Commands.PackBasket())
            .setTimeWindowUntil(Instant.now().plus(1, ChronoUnit.DAYS))
            .addSignatories(listOf(ourIdentity.owningKey))
            .toSignedTransaction()

        return try {
            // Record the transaction, no sessions are passed in as the transaction is only being
            // recorded locally
            utxoLedgerService.finalize(transaction, emptyList()).toString()
        } catch (e: Exception) {
            "Flow failed, message: ${e.message}"
        }
    }
}
