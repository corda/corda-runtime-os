package com.r3.corda.atomic.swap.workflows

import com.r3.corda.atomic.swap.contracts.AssetContract
import com.r3.corda.atomic.swap.contracts.LockContract
import com.r3.corda.atomic.swap.states.Asset
import com.r3.corda.atomic.swap.states.LockState
import net.corda.v5.application.flows.ClientRequestBody
import net.corda.v5.application.flows.ClientStartableFlow
import net.corda.v5.application.flows.CordaInject
import net.corda.v5.application.flows.FlowEngine
import net.corda.v5.application.marshalling.JsonMarshallingService
import net.corda.v5.application.membership.MemberLookup
import net.corda.v5.base.annotations.Suspendable
import net.corda.v5.crypto.SecureHash
import net.corda.v5.ledger.utxo.UtxoLedgerService
import org.slf4j.LoggerFactory
import java.lang.IllegalArgumentException
import java.time.Instant


data class ReclaimFlowArgs(val signedTransactionId: SecureHash, val stateId: String)

data class ReclaimFlowResult(val transactionId: String, val stateId: String, val ownerPublicKey: String)


class ReclaimFlow : ClientStartableFlow {

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
    lateinit var flowEngine: FlowEngine

    @Suspendable
    override fun call(requestBody: ClientRequestBody): String {
        log.info("UnlockFlow.call() called")

        try {
            val flowArgs = requestBody.getRequestBodyAs(jsonMarshallingService, ReclaimFlowArgs::class.java)

            val encumberedTx = ledgerService.findLedgerTransaction(flowArgs.signedTransactionId)
                ?: throw IllegalArgumentException("Unable to find transaction with id: ${flowArgs.signedTransactionId}")

            val myInfo = memberLookup.myInfo()
            val lockState = encumberedTx.getOutputStateAndRefs(LockState::class.java).singleOrNull()
                ?: throw IllegalStateException("Transaction with id: ${flowArgs.signedTransactionId} has no lock state")
            val assetState = encumberedTx.getOutputStateAndRefs(Asset::class.java).singleOrNull()
                ?: throw IllegalStateException("Transaction with id: ${flowArgs.signedTransactionId} has no asset state")
            val newAssetState = assetState.state.contractState.copy(participants = listOf(myInfo.ledgerKeys.first()))
            val timeWindowForReclaim = lockState.state.contractState.timeWindow.plusSeconds(31)

            val txBuilder = ledgerService.createTransactionBuilder()

                .setNotary(encumberedTx.notaryName)
                .setTimeWindowBetween(timeWindowForReclaim, Instant.now())
                .addInputState(lockState.ref)
                .addInputState(assetState.ref)
                .addOutputState(newAssetState)
                .addCommand(LockContract.LockCommands.Reclaim())
                .addCommand(AssetContract.AssetCommands.Transfer())
                .addSignatories(myInfo.ledgerKeys.first())

            val signedTransaction = txBuilder.toSignedTransaction()
            log.info("The signatories on unlock transaction are: ${signedTransaction.signatories}")

            val transactionId =
                flowEngine.subFlow(FinalizeFlow(signedTransaction, listOf(myInfo.name)))

            return jsonMarshallingService.format(
                ReclaimFlowResult(
                    transactionId,
                    newAssetState.assetId,
                    newAssetState.owner.toString()
                )
            )

        } catch (e: Exception) {
            log.warn("Failed to process utxo flow for request body '$requestBody' because: '${e.message}'")
            throw e
        }
    }
}
