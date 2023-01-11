package net.corda.simulator.runtime.ledger.utxo

import net.corda.simulator.SimulatorConfiguration
import net.corda.simulator.runtime.notary.SimTimeWindow
import net.corda.v5.application.crypto.SigningService
import net.corda.v5.application.persistence.PersistenceService
import net.corda.v5.application.serialization.SerializationService
import net.corda.v5.crypto.SecureHash
import net.corda.v5.ledger.common.Party
import net.corda.v5.ledger.utxo.*
import net.corda.v5.ledger.utxo.transaction.UtxoSignedTransaction
import net.corda.v5.ledger.utxo.transaction.UtxoTransactionBuilder
import java.security.PublicKey
import java.time.Instant

data class UtxoTransactionBuilderBase(
    override val notary: Party? = null,
    val timeWindow: TimeWindow? = null,
    val attachments: List<SecureHash> = emptyList(),
    val commands: List<Command> = emptyList(),
    val signatories: List<PublicKey> = emptyList(),
    val inputStateRefs: List<StateRef> = emptyList(),
    val referenceStateRefs: List<StateRef> = emptyList(),
    val outputStates: List<ContractState> = emptyList(),
    private val signingService: SigningService,
    private val configuration: SimulatorConfiguration,
    private val serializer: SerializationService,
    private val persistenceService: PersistenceService,
): UtxoTransactionBuilder {

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
        TODO("Not yet implemented")
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
        return copy(outputStates = outputStates + contractState)
    }

    override fun addOutputStates(vararg contractStates: ContractState): UtxoTransactionBuilder {
        return copy(outputStates = outputStates + contractStates)
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

    override fun getEncumbranceGroup(tag: String): List<ContractState> {
        TODO("Not yet implemented")
    }

    override fun getEncumbranceGroups(): Map<String, List<ContractState>> {
        TODO("Not yet implemented")
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

//    @Deprecated("To be replaced with parameter-less version")
//    override fun toSignedTransaction(signatory: PublicKey): UtxoSignedTransaction =
//        sign(listOf(signatory))

    fun sign(): UtxoSignedTransaction {
        val signatories = this.signatories
        check(!alreadySigned) { "The transaction cannot be signed twice." }
        require(signatories.toList().isNotEmpty()) {
            "At least one key needs to be provided in order to create a signed Transaction!"
        }
        verifyTx()

        // TODO Fix empty lists
        val unsignedTx = UtxoSignedTransactionBase(
            commands,
            inputStateRefs,
            notary!!,
            referenceStateRefs,
            this.signatories,
            emptyList(),
            timeWindow!!,
            outputStates,
            attachments,
            signingService,
            serializer,
            persistenceService,
            configuration,
        )
        val signedTx = unsignedTx.addSignature(signatories)
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
    }

}