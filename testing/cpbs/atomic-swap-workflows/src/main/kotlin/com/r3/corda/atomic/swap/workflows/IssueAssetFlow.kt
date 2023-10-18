package com.r3.corda.atomic.swap.workflows

import com.r3.corda.atomic.swap.contracts.AssetContract
import com.r3.corda.atomic.swap.states.Asset
import net.corda.v5.application.flows.ClientRequestBody
import net.corda.v5.application.flows.ClientStartableFlow
import net.corda.v5.application.flows.CordaInject
import net.corda.v5.application.flows.FlowEngine
import net.corda.v5.application.marshalling.JsonMarshallingService
import net.corda.v5.application.membership.MemberLookup
import net.corda.v5.base.annotations.Suspendable
import net.corda.v5.ledger.common.NotaryLookup
import net.corda.v5.ledger.utxo.UtxoLedgerService
import org.slf4j.LoggerFactory
import java.time.Duration
import java.time.Instant
import java.util.UUID

data class IssueAssetFlowArgs(val assetName: String)

data class IssueAssetFlowResult(val transactionId: String, val assetId: String, val ownerPublicKey: String)


class IssueAssetFlow : ClientStartableFlow {

    private companion object {
        val log = LoggerFactory.getLogger(this::class.java.enclosingClass)
    }

    @CordaInject
    lateinit var jsonMarshallingService: JsonMarshallingService

    @CordaInject
    lateinit var memberLookup: MemberLookup

    @CordaInject
    lateinit var ledgerService: UtxoLedgerService

    @CordaInject
    lateinit var notaryLookup: NotaryLookup

    @CordaInject
    lateinit var flowEngine: FlowEngine

    @Suspendable
    override fun call(requestBody: ClientRequestBody): String {
        log.info("IssueFlow.call() called")

        try {
            val flowArgs = requestBody.getRequestBodyAs(jsonMarshallingService, IssueAssetFlowArgs::class.java)

            val myInfo = memberLookup.myInfo()

            val outputState = Asset(
                myInfo.ledgerKeys.first(),
                flowArgs.assetName,
                UUID.randomUUID().toString(),
                listOf(myInfo.ledgerKeys.first())
            )

            val notaries = notaryLookup.notaryServices
            require(notaries.isNotEmpty()) { "No notaries are available." }
            require(notaries.size == 1) { "Too many notaries $notaries." }
            val notary = notaryLookup.notaryServices.single()

            val txBuilder = ledgerService.createTransactionBuilder()
                .setNotary(notary.name)
                .setTimeWindowBetween(Instant.now(), Instant.now().plusMillis(Duration.ofDays(1).toMillis()))
                .addOutputState(outputState)
                .addCommand(AssetContract.AssetCommands.Create())
                .addSignatories(outputState.participants)

            val signedTransaction = txBuilder.toSignedTransaction()

            val transactionId = flowEngine.subFlow(FinalizeFlow(signedTransaction, listOf(myInfo.name)))

            return jsonMarshallingService.format(IssueAssetFlowResult(transactionId, outputState.assetId, outputState.owner.toString()))

        } catch (e: Exception) {
            log.warn("Failed to process utxo flow for request body '$requestBody' because: '${e.message}'")
            throw e
        }
    }
}
