package com.r3.corda.atomic.swap.workflows

import com.r3.corda.atomic.swap.contracts.AssetContract
import com.r3.corda.atomic.swap.states.Asset
import com.r3.corda.atomic.swap.states.Member
import net.corda.v5.application.flows.ClientRequestBody
import net.corda.v5.application.flows.ClientStartableFlow
import net.corda.v5.application.flows.CordaInject
import net.corda.v5.application.marshalling.JsonMarshallingService
import net.corda.v5.application.membership.MemberLookup
import net.corda.v5.base.annotations.Suspendable
import net.corda.v5.ledger.common.NotaryLookup
import net.corda.v5.ledger.utxo.UtxoLedgerService
import org.slf4j.LoggerFactory
import java.time.Duration
import java.time.Instant
import java.util.*


data class CreateAssetFlowArgs(val assetName: String)

class CreateAssetFlow : ClientStartableFlow {

    private companion object {
        val log = LoggerFactory.getLogger(this::class.java.enclosingClass)
    }

    @CordaInject
    lateinit var jsonMarshallingService: JsonMarshallingService

    @CordaInject
    lateinit var memberLookup: MemberLookup

    // Injects the UtxoLedgerService to enable the flow to make use of the Ledger API.
    @CordaInject
    lateinit var ledgerService: UtxoLedgerService

    @CordaInject
    lateinit var notaryLookup: NotaryLookup

    @Suspendable
    override fun call(requestBody: ClientRequestBody): String {

        try {
            val flowArgs = requestBody.getRequestBodyAs(jsonMarshallingService, CreateAssetFlowArgs::class.java)
            val myInfo = memberLookup.myInfo()
            val notary = notaryLookup.notaryServices.single()

            val asset = Asset(
                Member(myInfo.name,myInfo.ledgerKeys[0]),
                flowArgs.assetName,
                UUID.randomUUID().toString(),
                listOf(myInfo.ledgerKeys[0])
            )

            val txBuilder= ledgerService.createTransactionBuilder()
                .setNotary(notary.name)
                .setTimeWindowBetween(Instant.now(), Instant.now().plusMillis(Duration.ofDays(1).toMillis()))
                .addOutputState(asset)
                .addCommand(AssetContract.AssetCommands.Create())
                .addSignatories(asset.participants)

            val signedTransaction = txBuilder.toSignedTransaction()

            val finalizedTransaction = ledgerService.finalize(signedTransaction, emptyList()).transaction

            return finalizedTransaction.id.toString()

        }
        // Catch any exceptions, log them and rethrow the exception.
        catch (e: Exception) {
            log.warn("Failed to process utxo flow for request body '$requestBody' because:'${e.message}'")
            throw e
        }
    }
}
/*
{
   "clientRequestId": "create-asset",
   "flowClassName": "com.r3.corda.atomic.swap.workflows.CreateAssetFlow",
   "requestBody": {
     "assetName": "My Asset"
   }
}
 */