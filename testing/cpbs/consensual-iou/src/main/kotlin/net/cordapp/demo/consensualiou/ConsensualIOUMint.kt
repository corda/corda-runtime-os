package net.cordapp.demo.consensualiou

import net.corda.v5.application.flows.CordaInject
import net.corda.v5.application.flows.Flow
import net.corda.v5.application.flows.FlowEngine
import net.corda.v5.application.flows.StartableByRPC
import net.corda.v5.base.annotations.Suspendable
import net.corda.v5.base.util.contextLogger

@StartableByRPC
class ConsensualIOUMint(val iouValue: Int, val otherParty: Party) : Flow<SignedTransaction> {

    private companion object {
        val log = contextLogger()
    }

    @CordaInject
    lateinit var flowEngine: FlowEngine

    @Suspendable
    override fun call(): String {
        // progressTracker.currentStep = GENERATING_TRANSACTION
        val iouState = IOUState(iouValue, myIdentity, otherParty)
        // Anything to verify here?
        val wireTransaction = new wireTransaction(iouState)


        // progressTracker.currentStep = SIGNING_TRANSACTION
        // val partSignedTx = serviceHub.signInitialTransaction(txBuilder)

        // progressTracker.currentStep = GATHERING_SIGS
        // val otherPartySession = initiateFlow(otherParty)
        // val fullySignedTx = subFlow(CollectSignaturesFlow(partSignedTx, setOf(otherPartySession), GATHERING_SIGS.childProgressTracker()))

        // progressTracker.currentStep = FINALISING_TRANSACTION
        // subFlow(FinalityFlow(fullySignedTx, setOf(otherPartySession), FINALISING_TRANSACTION.childProgressTracker()))

        return
    }
}

@InitiatedBy(ConsensualIOUMint::class)
class ConsensualIOUMintAccept(private val session: FlowSession) : Flow<String> {
    @Suspendable
    override fun call(): SignedTransaction {
        val signTransactionFlow = object : SignTransactionFlow(otherPartySession) {
            override fun checkTransaction(stx: SignedTransaction) = requireThat {
                val output = stx.tx.outputs.single().data
                "This must be an IOU transaction." using (output is IOUState)
                val iou = output as IOUState
                "I won't accept IOUs with a value over 100." using (iou.value <= 100)
            }
        }
        val txId = subFlow(signTransactionFlow).id

        return subFlow(ReceiveFinalityFlow(otherPartySession, expectedTxId = txId))
    }
}