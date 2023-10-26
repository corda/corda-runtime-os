package com.r3.corda.demo.interop.tokens.workflows.interop.rpc

import com.r3.corda.demo.interop.tokens.contracts.TokenContract
import com.r3.corda.demo.interop.tokens.states.TokenState
import net.corda.v5.application.flows.ClientRequestBody
import net.corda.v5.application.flows.ClientStartableFlow
import net.corda.v5.application.flows.CordaInject
import net.corda.v5.application.marshalling.InteropJsonMarshallingService
import net.corda.v5.application.membership.MemberLookup
import net.corda.v5.base.annotations.Suspendable
import net.corda.v5.base.exceptions.CordaRuntimeException
import net.corda.v5.base.types.MemberX500Name
import net.corda.v5.ledger.common.NotaryLookup
import net.corda.v5.ledger.utxo.UtxoLedgerService
import org.slf4j.LoggerFactory
import java.nio.ByteBuffer
import java.time.Duration
import java.time.Instant
import java.util.UUID


data class TransferDraftFlowArgs(val newOwner: String, val stateId: UUID)

data class RpcTransferDraftFlowResult(val transactionId: String, val stateId: UUID,val publicKey: ByteBuffer)

@Suppress("Unused")
class RpcTransferDraftFlow: ClientStartableFlow {

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
    lateinit var notaryLookup: NotaryLookup

    @Suspendable
    override fun call(requestBody: ClientRequestBody): String {
        log.info("RpcTransferDraftFlow.call() called")

        try {
            val flowArgs = requestBody.getRequestBodyAs(jsonMarshallingService, TransferDraftFlowArgs::class.java)

            val stateId = flowArgs.stateId

            val unconsumedStates = ledgerService.findUnconsumedStatesByExactType(
                TokenState::class.java, 10, Instant.now())
            val unconsumedStatesWithId = unconsumedStates.results .filter { it.state.contractState.linearId == stateId }

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

            val notary = requireNotNull(notaryLookup.lookup(stateAndRef.state.notaryName)){
                "Previous state's notary is not available"
            }
            val notaryKeyByteArray: ByteArray = notary.publicKey.encoded
            val notaryKeyByteBuffer: ByteBuffer = ByteBuffer.wrap(notaryKeyByteArray)

            val outputState = inputState.withNewOwner(newOwnerInfo.name, listOf(ownerInfo.ledgerKeys[0], newOwnerInfo.ledgerKeys[0]))

            val txBuilder = ledgerService.createTransactionBuilder()
                .setNotary(stateAndRef.state.notaryName)
                .setTimeWindowBetween(Instant.now(), Instant.now().plusMillis(Duration.ofDays(1).toMillis()))
                .addInputState(stateAndRef.ref)
                .addOutputState(outputState)
                .addCommand(TokenContract.Transfer())
                .addSignatories(outputState.participants)

            val signedTransaction = txBuilder.toSignedTransaction()

            ledgerService.persistDraftSignedTransaction(signedTransaction)


            return jsonMarshallingService.format(
                RpcTransferDraftFlowResult(
                    signedTransaction.id.toString(),
                    outputState.linearId,
                    notaryKeyByteBuffer
                )
            )

        } catch (e: Exception) {
            log.warn("Failed to process RpcTransferDraftFlow flow for request body '$requestBody' because: '${e.message}'")
            throw e
        }
    }
}
