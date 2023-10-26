package com.r3.corda.atomic.swap.workflows

import com.r3.corda.atomic.swap.contracts.AssetContract
import com.r3.corda.atomic.swap.contracts.LockContract
import com.r3.corda.atomic.swap.states.Asset
import com.r3.corda.atomic.swap.states.LockState
import net.corda.v5.application.crypto.DigitalSignatureAndMetadata
import net.corda.v5.application.flows.ClientRequestBody
import net.corda.v5.application.flows.ClientStartableFlow
import net.corda.v5.application.flows.CordaInject
import net.corda.v5.application.flows.FlowEngine
import net.corda.v5.application.marshalling.InteropJsonMarshallingService
import net.corda.v5.application.membership.MemberLookup
import net.corda.v5.base.annotations.Suspendable
import net.corda.v5.base.exceptions.CordaRuntimeException
import net.corda.v5.base.types.MemberX500Name
import net.corda.v5.ledger.utxo.UtxoLedgerService
import org.slf4j.LoggerFactory
import java.time.Duration
import java.time.Instant


data class UnlockFlowArgs(val newOwner: String, val stateId: String, val signature: DigitalSignatureAndMetadata)

data class UnlockFlowResult(val transactionId: String, val stateId: String, val ownerPublicKey: String)


class UnlockFlow : ClientStartableFlow {

    private companion object {
        val log = LoggerFactory.getLogger(this::class.java.enclosingClass)
    }

    @CordaInject
    lateinit var jsonMarshallingService: InteropJsonMarshallingService

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
            val flowArgs = requestBody.getRequestBodyAs(jsonMarshallingService, UnlockFlowArgs::class.java)

            val stateId = flowArgs.stateId

            val unconsumedLockStates = ledgerService.findUnconsumedStatesByType(LockState::class.java)
            val unconsumedLockStatesWithId = unconsumedLockStates.filter { it.state.contractState.assetId == stateId }

            if (unconsumedLockStatesWithId.size != 1) {
                throw CordaRuntimeException("Multiple or zero states with id '$stateId' found")
            }

            val stateAndRefLock = unconsumedLockStatesWithId.first()
            val inputLockState = stateAndRefLock.state.contractState


            val unconsumedAssetStates = ledgerService.findUnconsumedStatesByType(Asset::class.java)
            val unconsumedAssetStatesWithId = unconsumedAssetStates.filter { it.state.contractState.assetId == stateId }

            if (unconsumedAssetStatesWithId.size != 1) {
                throw CordaRuntimeException("Multiple or zero states with id '$stateId' found")
            }

            val stateAndRefAsset = unconsumedAssetStatesWithId.first()
            val inputAssetState = stateAndRefAsset.state.contractState

            val ownerInfo = memberLookup.lookup(inputLockState.creator)
                ?: throw CordaRuntimeException("MemberLookup can't find current state owner.")
            val newOwnerInfo = memberLookup.lookup(MemberX500Name.parse(flowArgs.newOwner))
                ?: throw CordaRuntimeException("MemberLookup can't find new state owner.")

            val newState =
                inputAssetState.withNewOwner(newOwnerInfo.ledgerKeys.first(), listOf(newOwnerInfo.ledgerKeys.first()))

            val txBuilder = ledgerService.createTransactionBuilder()

                .setNotary(stateAndRefAsset.state.notaryName)
                .setTimeWindowBetween(Instant.now(), Instant.now().plusMillis(Duration.ofDays(1).toMillis()))
                .addInputState(stateAndRefLock.ref)
                .addInputState(stateAndRefAsset.ref)
                .addOutputState(newState)
                .addCommand(LockContract.LockCommands.Unlock(flowArgs.signature))
                .addCommand(AssetContract.AssetCommands.Transfer())
                .addSignatories(newState.owner)

            val signedTransaction = txBuilder.toSignedTransaction()

            val transactionId =
                flowEngine.subFlow(FinalizeFlow(signedTransaction, listOf(ownerInfo.name, newOwnerInfo.name)))

            return jsonMarshallingService.format(
                UnlockFlowResult(
                    transactionId,
                    newState.assetId,
                    newState.owner.toString()
                )
            )

        } catch (e: Exception) {
            log.warn("Failed to process utxo flow for request body '$requestBody' because: '${e.message}'")
            throw e
        }
    }
}
