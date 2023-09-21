package com.r3.corda.atomic.swap.workflows

import com.r3.corda.atomic.swap.contracts.AssetContract
import com.r3.corda.atomic.swap.contracts.LockContract
import com.r3.corda.atomic.swap.states.Asset
import com.r3.corda.atomic.swap.states.LockState
import net.corda.v5.application.crypto.CompositeKeyGenerator
import net.corda.v5.application.flows.ClientRequestBody
import net.corda.v5.application.flows.ClientStartableFlow
import net.corda.v5.application.flows.CordaInject
import net.corda.v5.application.flows.FlowEngine
import net.corda.v5.application.marshalling.JsonMarshallingService
import net.corda.v5.application.membership.MemberLookup
import net.corda.v5.base.annotations.Suspendable
import net.corda.v5.base.exceptions.CordaRuntimeException
import net.corda.v5.base.types.MemberX500Name
import net.corda.v5.crypto.CompositeKeyNodeAndWeight
import net.corda.v5.ledger.utxo.UtxoLedgerService
import net.corda.v5.membership.MemberInfo
import org.slf4j.LoggerFactory
import java.security.PublicKey
import java.time.Duration
import java.time.Instant


data class TransferFlowArgs(val newOwner: String, val stateId: String)

data class TransferFlowResult(val transactionId: String, val stateId: String, val ownerPublicKey: String)


class TransferFlow: ClientStartableFlow {

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

    @CordaInject
    lateinit var compositeKeyGenerator: CompositeKeyGenerator

    @Suspendable
    override fun call(requestBody: ClientRequestBody): String {
        log.info("TransferFlow.call() called")

        try {
            val flowArgs = requestBody.getRequestBodyAs(jsonMarshallingService, TransferFlowArgs::class.java)

            val stateId = flowArgs.stateId

            val unconsumedStates = ledgerService.findUnconsumedStatesByType(Asset::class.java)
            val unconsumedStatesWithId = unconsumedStates.filter { it.state.contractState.assetId == stateId }

            if (unconsumedStatesWithId.size != 1) {
                throw CordaRuntimeException("Multiple or zero states with id '$stateId' found")
            }

            val stateAndRef = unconsumedStatesWithId.first()
            val inputState = stateAndRef.state.contractState

            val myInfo = memberLookup.myInfo()
            val ownerInfo = memberLookup.lookup(inputState.owner) ?:
            throw CordaRuntimeException("MemberLookup can't find current state owner.")
            val newOwnerInfo = memberLookup.lookup(MemberX500Name.parse(flowArgs.newOwner)) ?:
            throw CordaRuntimeException("MemberLookup can't find new state owner.")

            if (myInfo.name != ownerInfo.name) {
                throw CordaRuntimeException("Only the owner of a state can transfer it to a new owner.")
            }

            val lockState = LockState(
                inputState.owner,
                newOwnerInfo.ledgerKeys[0],
                inputState.assetName,
                inputState.assetId,
                listOf(inputState.owner, newOwnerInfo.ledgerKeys[0])
            )

            val assetWithCompositeKey = constructLockedAsset(lockState, newOwnerInfo)

            val outputState = inputState.withNewOwner(newOwnerInfo.ledgerKeys[0],
//                listOf(ownerInfo.ledgerKeys[0], newOwnerInfo.ledgerKeys[0]))
                listOf(assetWithCompositeKey.creator))

            val txBuilder = ledgerService.createTransactionBuilder()

                .setNotary(stateAndRef.state.notaryName)
                .setTimeWindowBetween(Instant.now(), Instant.now().plusMillis(Duration.ofDays(1).toMillis()))
                .addInputState(stateAndRef.ref)
                .addOutputState(lockState)
                .addOutputState(outputState)
                .addCommand(LockContract.LockCommands.Lock())
                .addCommand(AssetContract.AssetCommands.Transfer())
                .addSignatories(lockState.participants)
                .addSignatories(outputState.participants)

            val signedTransaction = txBuilder.toSignedTransaction()

            val transactionId = flowEngine.subFlow(FinalizeFlow(signedTransaction, listOf(ownerInfo.name, newOwnerInfo.name)))

            return jsonMarshallingService.format(TransferFlowResult(transactionId, outputState.assetId, outputState.owner.toString()))

        } catch (e: Exception) {
            log.warn("Failed to process utxo flow for request body '$requestBody' because: '${e.message}'")
            throw e
        }
    }

    private fun constructLockedAsset(asset: OwnableState, newOwner: MemberInfo): PublicKey {
        // Build composite key
        val compositeKey = compositeKeyGenerator.create(
            listOf(
                CompositeKeyNodeAndWeight(asset.owner, 1),
                CompositeKeyNodeAndWeight(newOwner.ledgerKeys.single(), 1)
            ), 1
        )

        //return asset.withNewOwner(compositeKey, listOf(asset.owner, newOwner.ledgerKeys[0]))
//        return LockState(compositeKey, newOwnerInfo.ledgerKeys[0], inputState.assetName, inputState.assetId,)
        return compositeKey
    }
}
