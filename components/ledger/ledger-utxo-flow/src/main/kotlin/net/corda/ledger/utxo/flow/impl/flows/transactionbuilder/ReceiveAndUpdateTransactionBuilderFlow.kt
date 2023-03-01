package net.corda.ledger.utxo.flow.impl.flows.transactionbuilder

import net.corda.ledger.utxo.flow.impl.flows.backchain.TransactionBackchainResolutionFlow
import net.corda.ledger.utxo.flow.impl.transaction.UtxoTransactionBuilderContainer
import net.corda.ledger.utxo.flow.impl.transaction.UtxoTransactionBuilderInternal
import net.corda.sandbox.CordaSystemFlow
import net.corda.utilities.trace
import net.corda.v5.application.flows.CordaInject
import net.corda.v5.application.flows.FlowEngine
import net.corda.v5.application.flows.SubFlow
import net.corda.v5.application.messaging.FlowSession
import net.corda.v5.base.annotations.Suspendable
import net.corda.v5.ledger.utxo.StateRef
import net.corda.v5.ledger.utxo.transaction.UtxoTransactionBuilder
import org.slf4j.Logger
import org.slf4j.LoggerFactory

@CordaSystemFlow
class ReceiveAndUpdateTransactionBuilderFlow(
    private val session: FlowSession,
    private val originalTransactionBuilder: UtxoTransactionBuilderInternal
) : SubFlow<UtxoTransactionBuilder> {

    @CordaInject
    lateinit var flowEngine: FlowEngine

    private companion object {
        val log: Logger = LoggerFactory.getLogger(ReceiveAndUpdateTransactionBuilderFlow::class.java)
    }

    private val updatedTransactionBuilder = originalTransactionBuilder.copy() as UtxoTransactionBuilderInternal

    @Suspendable
    override fun call(): UtxoTransactionBuilder {
        log.trace { "Starting receive and update transaction builder flow" }

        val knownTransactionIds =
            originalTransactionBuilder
                .inputStateRefs
                .map { it.transactionId }
                .toSet() +
            originalTransactionBuilder
                .referenceStateRefs
                .map { it.transactionId }
                .toSet()

        log.trace { "Waiting for transaction builder proposal." }
        val receivedTransactionBuilder = session.receive(UtxoTransactionBuilderContainer::class.java)
        updateProposedNotary(receivedTransactionBuilder)
        updateProposedTimeWindow(receivedTransactionBuilder)
        appendProposedAttachments(receivedTransactionBuilder)
        appendProposedCommands(receivedTransactionBuilder)
        appendProposedSignatories(receivedTransactionBuilder)
        val newStateRefs = appendProposedInputStateRefs(receivedTransactionBuilder) +
                appendProposedReferenceStateRefs(receivedTransactionBuilder)
        appendProposedOutputStates(receivedTransactionBuilder)

        log.trace { "Transaction builder proposals have been applied. Result: $updatedTransactionBuilder" }

        val newTransactionIds = newStateRefs
            .map { it.transactionId }
            .toSet() - knownTransactionIds

        if (newTransactionIds.isEmpty()) {
            log.trace { "There are no new states transferred, therefore no backchains need to be resolved." }
        } else {
            flowEngine.subFlow(TransactionBackchainResolutionFlow(newTransactionIds, session))
        }

        return updatedTransactionBuilder
    }

    private fun updateProposedNotary(payloadTransactionBuilder: UtxoTransactionBuilderContainer) {
        val notary = payloadTransactionBuilder.notary
        if (originalTransactionBuilder.notary == null && notary != null) {
            updatedTransactionBuilder.setNotary(notary)
        }
    }

    private fun updateProposedTimeWindow(payloadTransactionBuilder: UtxoTransactionBuilderContainer) {
        val timeWindow = payloadTransactionBuilder.timeWindow
        if (originalTransactionBuilder.timeWindow == null && timeWindow != null) {
            val timeWindowFrom = timeWindow.from
            if (timeWindowFrom != null) {
                updatedTransactionBuilder.setTimeWindowBetween(timeWindowFrom, timeWindow.until)
            } else {
                updatedTransactionBuilder.setTimeWindowUntil(timeWindow.until)

            }
        }
    }

    private fun appendProposedAttachments(payloadTransactionBuilder: UtxoTransactionBuilderContainer) {
        payloadTransactionBuilder.attachments
            .minus(originalTransactionBuilder.attachments.toSet())
            .distinct()
            .map {
                updatedTransactionBuilder.addAttachment(it)
            }
    }

    private fun appendProposedCommands(payloadTransactionBuilder: UtxoTransactionBuilderContainer) {
        payloadTransactionBuilder.commands
            .minus(originalTransactionBuilder.commands.toSet())
            .distinct()
            .map {
                updatedTransactionBuilder.addCommand(it)
            }
    }

    private fun appendProposedSignatories(payloadTransactionBuilder: UtxoTransactionBuilderContainer) {
        updatedTransactionBuilder
            .addSignatories(
                payloadTransactionBuilder.signatories
                    .minus(originalTransactionBuilder.signatories.toSet())
                    .distinct()
            )
    }

    private fun appendProposedInputStateRefs(payloadTransactionBuilder: UtxoTransactionBuilderContainer): List<StateRef> {
        val newInputStateRefs = payloadTransactionBuilder.inputStateRefs
            .minus(originalTransactionBuilder.inputStateRefs.toSet())
            .distinct()
        updatedTransactionBuilder.addInputStates(newInputStateRefs)
        return newInputStateRefs
    }

    private fun appendProposedReferenceStateRefs(payloadTransactionBuilder: UtxoTransactionBuilderContainer): List<StateRef> {
        val newReferenceStateRefs = payloadTransactionBuilder.referenceStateRefs
            .minus(originalTransactionBuilder.referenceStateRefs.toSet())
            .distinct()
        updatedTransactionBuilder.addReferenceStates(newReferenceStateRefs)
        return newReferenceStateRefs
    }

    private fun appendProposedOutputStates(payloadTransactionBuilder: UtxoTransactionBuilderContainer) {
        payloadTransactionBuilder.outputStates
            .minus(originalTransactionBuilder.outputStates.toSet())
            .distinct()
            .map {
                val encumbranceTag = it.encumbranceTag
                if (encumbranceTag != null) {
                    updatedTransactionBuilder.addEncumberedOutputStates(encumbranceTag, it.contractState)
                } else {
                    updatedTransactionBuilder.addOutputState(it.contractState)
                }
            }
    }
}
