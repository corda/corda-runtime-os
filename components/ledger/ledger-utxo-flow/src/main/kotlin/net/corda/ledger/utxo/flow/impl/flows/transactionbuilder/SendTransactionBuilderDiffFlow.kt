package net.corda.ledger.utxo.flow.impl.flows.transactionbuilder

import net.corda.ledger.utxo.flow.impl.flows.backchain.TransactionBackchainSenderFlow
import net.corda.ledger.utxo.flow.impl.transaction.ContractStateAndEncumbranceTag
import net.corda.ledger.utxo.flow.impl.transaction.UtxoTransactionBuilderContainer
import net.corda.ledger.utxo.flow.impl.transaction.UtxoTransactionBuilderInternal
import net.corda.sandbox.CordaSystemFlow
import net.corda.utilities.trace
import net.corda.v5.application.flows.CordaInject
import net.corda.v5.application.flows.FlowEngine
import net.corda.v5.application.flows.SubFlow
import net.corda.v5.application.messaging.FlowSession
import net.corda.v5.base.annotations.Suspendable
import net.corda.v5.crypto.SecureHash
import net.corda.v5.ledger.common.Party
import net.corda.v5.ledger.utxo.Command
import net.corda.v5.ledger.utxo.StateRef
import net.corda.v5.ledger.utxo.TimeWindow
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.security.PublicKey

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

    @Suspendable
    override fun call() {
        log.trace { "Starting send transaction builder flow." }

        val transactionBuilderDiff = UtxoTransactionBuilderContainer(
            proposeNotary(),
            proposeTimeWindow(),
            proposeAttachments(),
            proposeCommands(),
            proposeSignatories(),
            proposeInputStateRefs(),
            proposeReferenceStateRefs(),
            proposeOutputStates()
        )

        log.trace { "Sending proposed transaction builder parts." }
        session.send(transactionBuilderDiff)

        val newTransactionIds =
            (transactionBuilderDiff.inputStateRefs.toSet() + transactionBuilderDiff.referenceStateRefs.toSet())
                .map { it.transactionId }
                .toSet()
        if (newTransactionIds.isEmpty()) {
            log.trace { "There are no new states transferred, therefore no backchains need to be resolved." }
        } else {
            flowEngine.subFlow(TransactionBackchainSenderFlow(newTransactionIds, session))
        }
    }

    private fun proposeNotary(): Party? {
        return if (originalTransactionalBuilder.notary == null && transactionBuilder.notary != null) {
            transactionBuilder.notary
        } else {
            null
        }
    }

    private fun proposeTimeWindow(): TimeWindow? {
        return if (originalTransactionalBuilder.timeWindow == null && transactionBuilder.timeWindow != null) {
            transactionBuilder.timeWindow
        } else {
            null
        }
    }

    private fun proposeAttachments(): List<SecureHash> {
        return transactionBuilder.attachments -
                originalTransactionalBuilder.attachments.toSet()
    }

    private fun proposeCommands(): List<Command> {
        return transactionBuilder.commands -
                originalTransactionalBuilder.commands.toSet()
    }

    private fun proposeSignatories(): List<PublicKey> {
        return transactionBuilder.signatories -
                originalTransactionalBuilder.signatories.toSet()
    }

    private fun proposeInputStateRefs(): List<StateRef> {
        return transactionBuilder.inputStateRefs -
                originalTransactionalBuilder.inputStateRefs.toSet()
    }

    private fun proposeReferenceStateRefs(): List<StateRef> {
        return transactionBuilder.referenceStateRefs -
                originalTransactionalBuilder.referenceStateRefs.toSet()
    }

    private fun proposeOutputStates(): List<ContractStateAndEncumbranceTag> {
        return transactionBuilder.outputStates -
                originalTransactionalBuilder.outputStates.toSet()
    }
}
