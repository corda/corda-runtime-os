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
import net.corda.v5.base.exceptions.CordaRuntimeException
import net.corda.v5.base.types.MemberX500Name
import net.corda.v5.ledger.utxo.UtxoLedgerService
import org.slf4j.LoggerFactory
import java.time.Duration
import java.time.Instant


data class TransferFlowArgs(val newOwner: String, val stateId: String)

data class TransferFlowResult(val transactionId: String, val stateId: String, val ownerPublicKey: String)


class TransferFlow : ClientStartableFlow {

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
        log.info("TransferFlow.call() called")

        try {
            val flowArgs = requestBody.getRequestBodyAs(jsonMarshallingService, TransferFlowArgs::class.java)

            val stateId = flowArgs.stateId

            @Suppress("deprecation")
            val unconsumedStates = ledgerService.findUnconsumedStatesByType(Asset::class.java)
            val unconsumedStatesWithId = unconsumedStates.filter { it.state.contractState.assetId == stateId }

            if (unconsumedStatesWithId.size != 1) {
                throw CordaRuntimeException("Multiple or zero states with id '$stateId' found")
            }

            val stateAndRef = unconsumedStatesWithId.first()
            val inputState = stateAndRef.state.contractState

            val myInfo = memberLookup.myInfo()

            val newOwnerInfo = memberLookup.lookup(MemberX500Name.parse(flowArgs.newOwner))
                ?: throw CordaRuntimeException("MemberLookup can't find new state owner.")

            val outputState = inputState.withNewOwner(
                newOwnerInfo.ledgerKeys.first(),
                listOf(myInfo.ledgerKeys.first(), newOwnerInfo.ledgerKeys.first())
            )

            val txBuilder = ledgerService.createTransactionBuilder()

                .setNotary(stateAndRef.state.notaryName)
                .setTimeWindowBetween(Instant.now(), Instant.now().plusMillis(Duration.ofDays(1).toMillis()))
                .addInputState(stateAndRef.ref)
                .addOutputState(outputState)
                .addCommand(AssetContract.AssetCommands.Transfer())
                .addSignatories(outputState.participants)

            val signedTransaction = txBuilder.toSignedTransaction()

            val transactionId =
                flowEngine.subFlow(FinalizeFlow(signedTransaction, listOf(myInfo.name, newOwnerInfo.name)))

            return jsonMarshallingService.format(
                TransferFlowResult(
                    transactionId,
                    outputState.assetId,
                    outputState.owner.toString()
                )
            )

        } catch (e: Exception) {
            log.warn("Failed to process utxo flow for request body '$requestBody' because: '${e.message}'")
            throw e
        }
    }
}
