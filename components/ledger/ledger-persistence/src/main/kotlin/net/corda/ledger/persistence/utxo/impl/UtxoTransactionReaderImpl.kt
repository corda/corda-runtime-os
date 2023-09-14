package net.corda.ledger.persistence.utxo.impl

import net.corda.data.flow.event.external.ExternalEventContext
import net.corda.data.ledger.persistence.PersistTransaction
import net.corda.data.ledger.persistence.PersistTransactionIfDoesNotExist
import net.corda.ledger.common.data.transaction.PrivacySalt
import net.corda.ledger.common.data.transaction.SignedTransactionContainer
import net.corda.ledger.common.data.transaction.TransactionMetadataInternal
import net.corda.ledger.common.data.transaction.TransactionStatus
import net.corda.ledger.common.data.transaction.TransactionStatus.Companion.toTransactionStatus
import net.corda.ledger.persistence.utxo.UtxoPersistenceService
import net.corda.ledger.persistence.utxo.UtxoTransactionReader
import net.corda.ledger.utxo.data.state.cast
import net.corda.ledger.utxo.data.transaction.UtxoComponentGroup
import net.corda.ledger.utxo.data.transaction.UtxoLedgerTransactionImpl
import net.corda.ledger.utxo.data.transaction.UtxoTransactionOutputDto
import net.corda.ledger.utxo.data.transaction.WrappedUtxoWireTransaction
import net.corda.membership.lib.GroupParametersFactory
import net.corda.persistence.common.exceptions.MissingAccountContextPropertyException
import net.corda.persistence.common.getSerializationService
import net.corda.sandboxgroupcontext.SandboxGroupContext
import net.corda.utilities.serialization.deserialize
import net.corda.v5.application.crypto.DigitalSignatureAndMetadata
import net.corda.v5.base.exceptions.CordaRuntimeException
import net.corda.v5.crypto.SecureHash
import net.corda.v5.ledger.common.transaction.CordaPackageSummary
import net.corda.v5.ledger.utxo.ContractState
import net.corda.v5.ledger.utxo.StateAndRef
import net.corda.v5.ledger.utxo.StateRef
import net.corda.v5.ledger.utxo.transaction.UtxoLedgerTransaction
import net.corda.v5.membership.GroupParameters

class UtxoTransactionReaderImpl(
    sandbox: SandboxGroupContext,
    private val externalEventContext: ExternalEventContext,
    transaction: ByteArray,
    override val status: TransactionStatus,
    override val visibleStatesIndexes: List<Int>,
    private val groupParametersFactory: GroupParametersFactory
) : UtxoTransactionReader {

    constructor(
        sandbox: SandboxGroupContext,
        externalEventContext: ExternalEventContext,
        transaction: PersistTransaction,
        groupParametersFactory: GroupParametersFactory
    ) : this(
        sandbox,
        externalEventContext,
        transaction.transaction.array(),
        transaction.status.toTransactionStatus(),
        transaction.visibleStatesIndexes,
        groupParametersFactory
    )

    constructor(
        sandbox: SandboxGroupContext,
        externalEventContext: ExternalEventContext,
        transaction: PersistTransactionIfDoesNotExist,
        groupParametersFactory: GroupParametersFactory
    ) : this(
        sandbox,
        externalEventContext,
        transaction.transaction.array(),
        transaction.status.toTransactionStatus(),
        emptyList(),
        groupParametersFactory
    )

    private companion object {
        const val CORDA_ACCOUNT = "corda.account"
    }

    private val serializer = sandbox.getSerializationService()
    private val signedTransaction = serializer.deserialize<SignedTransactionContainer>(transaction)
    private val wrappedWireTransaction = WrappedUtxoWireTransaction(signedTransaction.wireTransaction, serializer)

    override val id: SecureHash
        get() = signedTransaction.id

    override val account: String
        get() = externalEventContext.contextProperties.items.find { it.key == CORDA_ACCOUNT }?.value
            ?: throw MissingAccountContextPropertyException()

    override val privacySalt: PrivacySalt
        get() = signedTransaction.wireTransaction.privacySalt

    override val rawGroupLists: List<List<ByteArray>>
        get() = signedTransaction.wireTransaction.componentGroupLists

    override val signatures: List<DigitalSignatureAndMetadata>
        get() = signedTransaction.signatures

    override val cpkMetadata: List<CordaPackageSummary>
        get() = (signedTransaction.wireTransaction.metadata as TransactionMetadataInternal).getCpkMetadata()

    override fun getVisibleStates(): Map<Int, StateAndRef<ContractState>> {
        val visibleStatesSet = visibleStatesIndexes.toSet()
        return rawGroupLists[UtxoComponentGroup.OUTPUTS.ordinal]
            .zip(rawGroupLists[UtxoComponentGroup.OUTPUTS_INFO.ordinal])
            .withIndex()
            .filter { indexed -> visibleStatesSet.contains(indexed.index) }
            .associate { (index, value) ->
                index to UtxoTransactionOutputDto(id.toString(), index, value.second, value.first).toStateAndRef(
                    serializer
                )
            }
    }

    override fun getConsumedStates(persistenceService: UtxoPersistenceService): List<StateAndRef<ContractState>> {
        val inputsGroupedByTransactionId = wrappedWireTransaction.inputStateRefs.groupBy { it.transactionId }

        return inputsGroupedByTransactionId.flatMap { inputsByTransactionId ->

            val (transaction, _) = persistenceService.findSignedTransaction(
                id = inputsByTransactionId.key.toString(),
                transactionStatus = TransactionStatus.VERIFIED
            )

            requireNotNull(transaction) { "Failed to load transaction ${inputsByTransactionId.key}" }

            val wrappedTransaction = WrappedUtxoWireTransaction(transaction.wireTransaction, serializer)

            inputsByTransactionId.value.map { stateRef ->
                wrappedTransaction.outputStateAndRefs[stateRef.index].cast(ContractState::class.java)
            }
        }
    }

    override fun getConsumedStateRefs(): List<StateRef> = wrappedWireTransaction.inputStateRefs

    override fun getUtxoTransaction(persistenceService: UtxoPersistenceService): UtxoLedgerTransaction? {

        val stateRefsToStateAndRefs = resolveStateRefs(persistenceService).associateBy { it.ref }
        val inputStateAndRefs = getInputAndRefs(stateRefsToStateAndRefs)
        val referenceStateAndRefs = getReferenceStateAndRefs(stateRefsToStateAndRefs)
        val groupParameters = getGroupParameters(persistenceService)

        val utxoLedgerTransaction = UtxoLedgerTransactionImpl(
            wrappedWireTransaction, inputStateAndRefs, referenceStateAndRefs, groupParameters
        )
        return utxoLedgerTransaction
    }

    private fun resolveStateRefs(persistenceService: UtxoPersistenceService): List<StateAndRef<*>> {
        val allStateRefs =
            (wrappedWireTransaction.inputStateRefs + wrappedWireTransaction.referenceStateRefs).distinct()

        return persistenceService.resolveStateRefs(allStateRefs).map { it.toStateAndRef<ContractState>(serializer) }
    }

    private fun getInputAndRefs(stateRefsToStateAndRefs: Map<StateRef, StateAndRef<*>>) =
        wrappedWireTransaction.inputStateRefs.map {
            stateRefsToStateAndRefs[it]
                ?: throw (CordaRuntimeException("Could not find StateRef $it when resolving input states."))
        }

    private fun getReferenceStateAndRefs(stateRefsToStateAndRefs: Map<StateRef, StateAndRef<*>>) =
        wrappedWireTransaction.referenceStateRefs.map {
            stateRefsToStateAndRefs[it]
                ?: throw (CordaRuntimeException("Could not find StateRef $it when resolving reference states."))
        }

    private fun getGroupParameters(persistenceService: UtxoPersistenceService): GroupParameters {

        val membershipGroupParametersHashString =
            requireNotNull((wrappedWireTransaction.metadata as TransactionMetadataInternal).getMembershipGroupParametersHash()) {
                "Membership group parameters hash cannot be found in the transaction metadata."
            }
        val groupParameters = persistenceService.findSignedGroupParameters(membershipGroupParametersHashString)
        requireNotNull(groupParameters) {
            "Signed group parameters $membershipGroupParametersHashString related to the transaction " +
                    "${wrappedWireTransaction.id} cannot be accessed."
        }

        return groupParametersFactory.create(groupParameters)
    }
}
