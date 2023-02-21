package net.corda.ledger.utxo.flow.impl.flows.transactionbuilder

import net.corda.ledger.utxo.flow.impl.flows.backchain.TransactionBackchainSenderFlow
import net.corda.ledger.utxo.flow.impl.transaction.UtxoTransactionBuilderContainer
import net.corda.ledger.utxo.flow.impl.transaction.UtxoTransactionBuilderInternal
import net.corda.sandbox.CordaSystemFlow
import net.corda.utilities.trace
import net.corda.v5.application.flows.CordaInject
import net.corda.v5.application.flows.FlowEngine
import net.corda.v5.application.flows.SubFlow
import net.corda.v5.application.messaging.FlowSession
import net.corda.v5.base.annotations.Suspendable
import org.slf4j.Logger
import org.slf4j.LoggerFactory

@CordaSystemFlow
class SendTransactionBuilderDiffFlow(
    private val transactionBuilder: UtxoTransactionBuilderInternal,
    private val session: FlowSession,
    private val originalTransactionalBuilder: UtxoTransactionBuilderInternal
) : SubFlow<Unit> {

    @CordaInject
    lateinit var flowEngine: FlowEngine

    private companion object {
        val log: Logger = LoggerFactory.getLogger(SendTransactionBuilderDiffFlow::class.java)
    }

    private val transactionBuilderDiff = UtxoTransactionBuilderContainer()

    @Suspendable
    override fun call() {
        log.trace { "Starting send transaction builder flow." }

        proposeNotary()
        proposeTimeWindow()
        proposeAttachments()
        proposeCommands()
        proposeSignatories()
        proposeInputStateRefs()
        proposeReferenceStateRefs()
        proposeOutputStates()

        log.trace { "Sending proposed transaction builder parts." }
        session.send(transactionBuilderDiff)

        val newTransactionIds =
            (transactionBuilderDiff.inputStateRefs.toSet() + transactionBuilderDiff.referenceStateRefs.toSet())
                .map { it.transactionId }
                .toSet()
        flowEngine.subFlow(TransactionBackchainSenderFlow(newTransactionIds, session))
    }

    private fun proposeNotary() {
        if (originalTransactionalBuilder.notary == null && transactionBuilder.notary != null) {
            transactionBuilderDiff.notary = transactionBuilder.notary
        }
    }

    private fun proposeTimeWindow() {
        if (originalTransactionalBuilder.timeWindow == null && transactionBuilder.timeWindow != null) {
            transactionBuilderDiff.timeWindow = transactionBuilder.timeWindow
        }
    }

    private fun proposeAttachments() {
        transactionBuilderDiff.attachments += transactionBuilder.attachments.filter {
            it !in originalTransactionalBuilder.attachments
        }
    }

    private fun proposeCommands() {
        transactionBuilderDiff.commands += transactionBuilder.commands.filter {
            it !in originalTransactionalBuilder.commands
        }
    }

    private fun proposeSignatories() {
        transactionBuilderDiff.signatories += transactionBuilder.signatories.filter {
            it !in originalTransactionalBuilder.signatories
        }
    }

    private fun proposeInputStateRefs() {
        transactionBuilderDiff.inputStateRefs += transactionBuilder.inputStateRefs.filter {
            it !in originalTransactionalBuilder.inputStateRefs
        }
    }

    private fun proposeReferenceStateRefs() {
        transactionBuilderDiff.referenceStateRefs += transactionBuilder.referenceStateRefs.filter {
            it !in originalTransactionalBuilder.referenceStateRefs
        }
    }

    private fun proposeOutputStates() {
        transactionBuilderDiff.outputStates += transactionBuilder.outputStates.filter {
            it !in originalTransactionalBuilder.outputStates
        }
    }
}
