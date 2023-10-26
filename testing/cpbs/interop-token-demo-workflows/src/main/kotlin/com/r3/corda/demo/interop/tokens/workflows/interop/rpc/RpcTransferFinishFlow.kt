package com.r3.corda.demo.interop.tokens.workflows.interop.rpc

import com.r3.corda.demo.interop.tokens.states.TokenState
import net.corda.v5.application.crypto.DigestService
import net.corda.v5.application.crypto.DigitalSignatureAndMetadata
import net.corda.v5.application.flows.ClientRequestBody
import net.corda.v5.application.flows.ClientStartableFlow
import net.corda.v5.application.flows.CordaInject
import net.corda.v5.application.flows.FlowEngine
import net.corda.v5.application.marshalling.InteropJsonMarshallingService
import net.corda.v5.application.membership.MemberLookup
import net.corda.v5.base.annotations.Suspendable
import net.corda.v5.base.exceptions.CordaRuntimeException
import net.corda.v5.ledger.utxo.UtxoLedgerService
import org.slf4j.LoggerFactory
import java.util.UUID


data class TransferFinishFlowArgs(val draftTransactionId: String)

data class RpcTransferFinishFlowResult(
    val transactionId: String,
    val stateId: UUID,
    val signature: DigitalSignatureAndMetadata
)

@Suppress("Unused")
class RpcTransferFinishFlow: ClientStartableFlow {

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

    @CordaInject
    lateinit var digestService: DigestService

    @Suspendable
    override fun call(requestBody: ClientRequestBody): String {
        log.info("RpcTransferFinishFlow.call() called")

        try {
            val flowArgs = requestBody.getRequestBodyAs(jsonMarshallingService, TransferFinishFlowArgs::class.java)

            val draftTransactionIdString = flowArgs.draftTransactionId
            val draftTransactionId = digestService.parseSecureHash(draftTransactionIdString)
            val signedTransaction = requireNotNull(ledgerService.findDraftSignedTransaction(draftTransactionId) ){
                "Draft transaction $draftTransactionIdString not found!"
            }
            val outputState = signedTransaction.outputStateAndRefs.first().state.contractState as TokenState
            val participants = outputState.participants
            val oldOwnerPublicKey = participants[0]
            val newOwnerPublicKey = participants[1]
            val ownerInfo = memberLookup.lookup(oldOwnerPublicKey) ?:
                throw CordaRuntimeException("MemberLookup can't find old state owner.")
            val newOwnerInfo = memberLookup.lookup(newOwnerPublicKey) ?:
                throw CordaRuntimeException("MemberLookup can't find new state owner.")

            if (memberLookup.myInfo().name != ownerInfo.name) {
                throw CordaRuntimeException("Only the owner of a state can transfer it to a new owner.")
            }

            val notarySignature: DigitalSignatureAndMetadata = flowEngine.subFlow(
                RpcFinalizeFlow(signedTransaction, listOf(ownerInfo.name, newOwnerInfo.name)))

            return jsonMarshallingService.format(RpcTransferFinishFlowResult(signedTransaction.id.toString(), outputState.linearId,
                notarySignature))

        } catch (e: Exception) {
            log.warn("Failed to process RpcTransferFinishFlow flow for request body '$requestBody' because: '${e.message}'")
            throw e
        }
    }
}
