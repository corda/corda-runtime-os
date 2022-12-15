package net.corda.ledger.persistence.utxo.impl

import net.corda.data.flow.event.external.ExternalEventContext
import net.corda.data.ledger.persistence.PersistTransaction
import net.corda.ledger.common.data.transaction.SignedTransactionContainer
import net.corda.ledger.common.data.transaction.TransactionStatus
import net.corda.ledger.common.data.transaction.TransactionStatus.Companion.toTransactionStatus
import net.corda.ledger.persistence.utxo.UtxoPersistenceService
import net.corda.ledger.persistence.utxo.UtxoTransactionReader
import net.corda.ledger.utxo.data.state.StateAndRefImpl
import net.corda.ledger.utxo.data.state.TransactionStateImpl
import net.corda.ledger.utxo.data.transaction.UtxoComponentGroup
import net.corda.ledger.utxo.data.transaction.UtxoOutputInfoComponent
import net.corda.ledger.utxo.data.transaction.WrappedUtxoWireTransaction
import net.corda.persistence.common.exceptions.NullParameterException
import net.corda.persistence.common.getSerializationService
import net.corda.sandboxgroupcontext.SandboxGroupContext
import net.corda.v5.application.crypto.DigitalSignatureAndMetadata
import net.corda.v5.application.serialization.deserialize
import net.corda.v5.crypto.SecureHash
import net.corda.v5.ledger.common.transaction.CordaPackageSummary
import net.corda.v5.ledger.common.transaction.PrivacySalt
import net.corda.v5.ledger.utxo.ContractState
import net.corda.v5.ledger.utxo.StateAndRef
import net.corda.v5.ledger.utxo.StateRef

class UtxoTransactionReaderImpl(
    sandbox: SandboxGroupContext,
    private val externalEventContext: ExternalEventContext,
    private val transaction: PersistTransaction
) : UtxoTransactionReader {

    private companion object {
        const val CORDA_ACCOUNT = "corda.account"
    }

    private val serializer = sandbox.getSerializationService()
    private val signedTransaction = serializer.deserialize<SignedTransactionContainer>(transaction.transaction.array())
    private val wrappedWireTransaction = WrappedUtxoWireTransaction(signedTransaction.wireTransaction, serializer)


    override val id: SecureHash
        get() = signedTransaction.id

    override val account: String
        get() = externalEventContext.contextProperties.items.find { it.key == CORDA_ACCOUNT }?.value
            ?: throw NullParameterException("Flow external event context property '${CORDA_ACCOUNT}' not set")

    override val status: TransactionStatus
        get() = transaction.status.toTransactionStatus()

    override val privacySalt: PrivacySalt
        get() = signedTransaction.wireTransaction.privacySalt

    override val rawGroupLists: List<List<ByteArray>>
        get() = signedTransaction.wireTransaction.componentGroupLists

    override val signatures: List<DigitalSignatureAndMetadata>
        get() = signedTransaction.signatures

    override val cpkMetadata: List<CordaPackageSummary>
        get() = signedTransaction.wireTransaction.metadata.getCpkMetadata()

    override val relevantStatesIndexes: List<Int>
        get() = transaction.relevantStatesIndexes ?: emptyList()

    override fun getProducedStates(): List<StateAndRef<ContractState>> {
        return rawGroupLists[UtxoComponentGroup.OUTPUTS.ordinal].zip(rawGroupLists[UtxoComponentGroup.OUTPUTS_INFO.ordinal])
            .withIndex()
            .filter { indexed -> indexed.index !in relevantStatesIndexes }
            .map { (index, value) ->
                Triple(
                    index,
                    serializer.deserialize<ContractState>(value.first),
                    serializer.deserialize<UtxoOutputInfoComponent>(value.second)
                )
            }
            .map { (index, state, info) ->
                StateAndRefImpl(
                    state = TransactionStateImpl(state, info.notary, info.encumbrance),
                    ref = StateRef(id, index)
                )
            }
    }

    override fun getConsumedStates(persistenceService: UtxoPersistenceService): List<StateAndRef<ContractState>> {
        return wrappedWireTransaction.inputStateRefs.groupBy { it.transactionHash }
            .flatMap { inputsByTransaction ->
                // this is not the most efficient way of doing this - to be fixed in CORE-8971
                val tx =
                    persistenceService.findTransaction(inputsByTransaction.key.toString(), TransactionStatus.VERIFIED)
                requireNotNull(tx) { "Failed to load transaction ${inputsByTransaction.key}" }
                val wrappedTx = WrappedUtxoWireTransaction(tx.wireTransaction, serializer)
                inputsByTransaction.value.map { ref -> wrappedTx.outputStateAndRefs[ref.index] }
            }
    }

    override fun getConsumedStateRefs(): List<StateRef> = wrappedWireTransaction.inputStateRefs

}
