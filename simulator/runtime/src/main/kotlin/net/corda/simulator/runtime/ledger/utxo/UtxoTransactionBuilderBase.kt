package net.corda.simulator.runtime.ledger.utxo

import net.corda.simulator.SimulatorConfiguration
import net.corda.simulator.runtime.notary.SimTimeWindow
import net.corda.simulator.runtime.serialization.BaseSerializationService
import net.corda.v5.application.crypto.SigningService
import net.corda.v5.application.persistence.PersistenceService
import net.corda.v5.base.annotations.CordaSerializable
import net.corda.v5.crypto.SecureHash
import net.corda.v5.ledger.common.Party
import net.corda.v5.ledger.utxo.Command
import net.corda.v5.ledger.utxo.ContractState
import net.corda.v5.ledger.utxo.StateRef
import net.corda.v5.ledger.utxo.TimeWindow
import net.corda.v5.ledger.utxo.TransactionState
import net.corda.v5.ledger.utxo.EncumbranceGroup
import net.corda.v5.ledger.utxo.transaction.UtxoSignedTransaction
import net.corda.v5.ledger.utxo.transaction.UtxoTransactionBuilder
import java.security.PublicKey
import java.time.Instant

@Suppress("TooManyFunctions")
data class UtxoTransactionBuilderBase(
    private val notary: Party? = null,
    val timeWindow: TimeWindow? = null,
    val attachments: List<SecureHash> = emptyList(),
    val commands: List<Command> = emptyList(),
    val signatories: List<PublicKey> = emptyList(),
    val inputStateRefs: List<StateRef> = emptyList(),
    val referenceStateRefs: List<StateRef> = emptyList(),
    val outputStates: List<ContractStateAndEncumbranceTag> = emptyList(),
    private val signingService: SigningService,
    private val configuration: SimulatorConfiguration,
    private val persistenceService: PersistenceService,
): UtxoTransactionBuilder {

    private val serializer = BaseSerializationService()
    private var alreadySigned = false

    override fun addAttachment(attachmentId: SecureHash): UtxoTransactionBuilder {
        return copy(attachments = attachments + attachmentId)
    }

    override fun addCommand(command: Command): UtxoTransactionBuilder {
        return copy(commands = commands + command)
    }

    override fun addEncumberedOutputStates(tag: String, vararg contractStates: ContractState): UtxoTransactionBuilder {
        return addEncumberedOutputStates(tag, contractStates.toList())
    }

    override fun addEncumberedOutputStates(
        tag: String,
        contractStates: Iterable<ContractState>
    ): UtxoTransactionBuilder {
        return copy(outputStates = outputStates + contractStates.map { it.withEncumbrance(tag) })
    }

    override fun addInputState(stateRef: StateRef): UtxoTransactionBuilder {
        return copy(inputStateRefs = inputStateRefs + stateRef)
    }

    override fun addInputStates(vararg stateRefs: StateRef): UtxoTransactionBuilder {
        return copy(inputStateRefs = inputStateRefs + stateRefs)
    }

    override fun addInputStates(stateRefs: Iterable<StateRef>): UtxoTransactionBuilder {
        return addInputStates(stateRefs.toList())
    }

    override fun addOutputState(contractState: ContractState): UtxoTransactionBuilder {
        return copy(outputStates = outputStates + contractState.withEncumbrance(null))
    }

    override fun addOutputStates(vararg contractStates: ContractState): UtxoTransactionBuilder {
        return copy(outputStates = outputStates + contractStates.map { it.withEncumbrance(null) })
    }

    override fun addOutputStates(contractStates: Iterable<ContractState>): UtxoTransactionBuilder {
        return addOutputStates(contractStates.toList())
    }

    override fun addReferenceState(stateRef: StateRef): UtxoTransactionBuilder {
        return copy(referenceStateRefs = referenceStateRefs + stateRef)
    }

    override fun addReferenceStates(vararg stateRefs: StateRef): UtxoTransactionBuilder {
        return copy(referenceStateRefs = referenceStateRefs + stateRefs)
    }

    override fun addReferenceStates(stateRefs: Iterable<StateRef>): UtxoTransactionBuilder {
        return addReferenceStates(stateRefs.toList())
    }

    override fun addSignatories(signatories: Iterable<PublicKey>): UtxoTransactionBuilder {
        return copy(signatories = this.signatories + signatories)
    }

    override fun addSignatories(vararg signatories: PublicKey): UtxoTransactionBuilder {
        return addSignatories(signatories.toList())
    }

    override fun getEncumbranceGroup(tag: String): List<ContractState> {
        return requireNotNull(encumbranceGroups[tag]) {
            "Encumbrance group with the specified tag does not exist: $tag."
        }
    }

    override fun getEncumbranceGroups(): Map<String, List<ContractState>> {
        return outputStates
            .filter { outputState -> outputState.encumbranceTag != null }
            .groupBy { outputState -> outputState.encumbranceTag }
            .map { entry -> entry.key!! to entry.value.map { items -> items.contractState } }
            .toMap()
    }

    override fun getNotary(): Party? {
        return notary
    }

    override fun setNotary(notary: Party): UtxoTransactionBuilder {
        return copy(notary = notary)
    }

    override fun setTimeWindowBetween(from: Instant, until: Instant): UtxoTransactionBuilder {
        return copy(timeWindow = SimTimeWindow(from, until))
    }

    override fun setTimeWindowUntil(until: Instant): UtxoTransactionBuilder {
        return copy(timeWindow = SimTimeWindow(null, until))
    }

    override fun toSignedTransaction(): UtxoSignedTransaction = sign()

    private fun sign(): UtxoSignedTransaction {
        val signatories = this.signatories
        check(!alreadySigned) { "The transaction cannot be signed twice." }
        verifyTx()

        val unsignedTx = UtxoSignedTransactionBase(
            emptyList(),
            UtxoStateLedgerInfo(
                commands,
                inputStateRefs,
                notary!!,
                referenceStateRefs,
                signatories,
                timeWindow!!,
                outputStates,
                attachments
            ),
            signingService,
            serializer,
            persistenceService,
            configuration,
        )
        val signedTx = unsignedTx.addSignatures(signatories)
        alreadySigned = true
        return signedTx
    }

    private fun verifyTx() {
        checkNotNull(notary) {
            "The notary of UtxoTransactionBuilder must not be null."
        }
        checkNotNull(timeWindow) {
            "The time window of UtxoTransactionBuilder must not be null."
        }

        check(encumbranceGroups.all { it.value.size > 1 }) {
            "Every encumbrance group of the current UtxoTransactionBuilder must contain more than one output state."
        }

        check(signatories.isNotEmpty()) {
            "At least one signatory signing key must be applied to the current transaction."
        }
        check(inputStateRefs.isNotEmpty() ||
                outputStates.isNotEmpty()) {
            "At least one input state, or one output state must be applied to the current transaction."
        }
        check(commands.isNotEmpty()) {
            "At least one command must be applied to the current transaction."
        }
    }

    private fun ContractState.withEncumbrance(tag: String?): ContractStateAndEncumbranceTag {
        return ContractStateAndEncumbranceTag(this, tag)
    }

}

@CordaSerializable
data class ContractStateAndEncumbranceTag(val contractState: ContractState, val encumbranceTag: String?) {

    fun toTransactionState(notary: Party, encumbranceGroupSize: Int?): TransactionState<*> {
        return SimTransactionState(contractState, notary, encumbranceTag?.let{
            requireNotNull(encumbranceGroupSize)
            SimEncumbranceGroup(encumbranceGroupSize, it)
        })
    }
}

data class SimEncumbranceGroup(private val size: Int, private val tag: String) : EncumbranceGroup {

    override fun getTag(): String {
        return tag
    }

    override fun getSize(): Int {
        return size
    }
}