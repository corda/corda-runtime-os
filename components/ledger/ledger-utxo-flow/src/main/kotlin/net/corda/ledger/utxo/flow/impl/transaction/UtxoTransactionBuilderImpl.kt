package net.corda.ledger.utxo.flow.impl.transaction

import net.corda.ledger.common.data.transaction.CordaPackageSummary
import net.corda.ledger.common.data.transaction.PrivacySaltImpl
import net.corda.ledger.common.data.transaction.TransactionMetadata
import net.corda.ledger.common.data.transaction.WireTransaction
import net.corda.ledger.common.flow.impl.transaction.createTransactionSignature
import net.corda.ledger.utxo.data.state.TransactionStateImpl
import net.corda.ledger.utxo.data.transaction.UtxoComponentGroup
import net.corda.ledger.utxo.data.transaction.UtxoOutputInfoComponent
import net.corda.ledger.utxo.flow.impl.timewindow.TimeWindowBetweenImpl
import net.corda.ledger.utxo.flow.impl.timewindow.TimeWindowUntilImpl
import net.corda.sandbox.SandboxGroup
import net.corda.v5.application.crypto.DigitalSignatureVerificationService
import net.corda.v5.application.crypto.SigningService
import net.corda.v5.application.marshalling.JsonMarshallingService
import net.corda.v5.application.serialization.SerializationService
import net.corda.v5.base.annotations.Suspendable
import net.corda.v5.cipher.suite.CipherSchemeMetadata
import net.corda.v5.cipher.suite.DigestService
import net.corda.v5.cipher.suite.merkle.MerkleTreeProvider
import net.corda.v5.crypto.SecureHash
import net.corda.v5.ledger.common.Party
import net.corda.v5.ledger.utxo.Command
import net.corda.v5.ledger.utxo.ContractState
import net.corda.v5.ledger.utxo.StateAndRef
import net.corda.v5.ledger.utxo.TimeWindow
import net.corda.v5.ledger.utxo.transaction.UtxoSignedTransaction
import net.corda.v5.ledger.utxo.transaction.UtxoTransactionBuilder
import java.security.PublicKey
import java.time.Instant

@Suppress("TooManyFunctions")
data class UtxoTransactionBuilderImpl(
    private val cipherSchemeMetadata: CipherSchemeMetadata,
    private val digestService: DigestService,
    private val jsonMarshallingService: JsonMarshallingService,
    private val merkleTreeProvider: MerkleTreeProvider,
    private val serializationService: SerializationService,
    private val signingService: SigningService,
    private val digitalSignatureVerificationService: DigitalSignatureVerificationService,
    private val currentSandboxGroup: SandboxGroup, // TODO CORE-7101 use CurrentSandboxService when it gets available
    // cpi defines what type of signing/hashing is used (related to the digital signature signing and verification stuff)
    private val transactionMetadata: TransactionMetadata,
    override val notary: Party? = null,
    private val timeWindow: TimeWindow? = null,
    private val attachments: List<SecureHash> = emptyList(),
    private val commands: List<Command> = emptyList(),
    private val signatories: Set<PublicKey> = emptySet(),
    private val inputStateAndRefs: List<StateAndRef<*>> = emptyList(),
    private val referenceInputStateAndRefs: List<StateAndRef<*>> = emptyList(),

    // We cannot use TransactionStates without notary which may be available only later
    private val outputStates: List<Pair<ContractState, Int?>> = emptyList()
) : UtxoTransactionBuilder {

    override fun setNotary(notary: Party): UtxoTransactionBuilder {
        return copy(notary = notary)
    }

    override fun addAttachment(attachmentId: SecureHash): UtxoTransactionBuilder {
        return copy(attachments = attachments + attachmentId)
    }

    override fun addCommand(command: Command): UtxoTransactionBuilder {
        return copy(commands = commands + command)
    }

    override fun addSignatories(signatories: Iterable<PublicKey>): UtxoTransactionBuilder {
        return copy(signatories = this.signatories + signatories)
    }

    override fun addCommandAndSignatories(command: Command, signatories: Iterable<PublicKey>): UtxoTransactionBuilder {
        return addCommand(command).addSignatories(signatories)
    }

    override fun addInputState(stateAndRef: StateAndRef<*>): UtxoTransactionBuilder {
        return copy(inputStateAndRefs = inputStateAndRefs + stateAndRef)
    }

    override fun addReferenceInputState(stateAndRef: StateAndRef<*>): UtxoTransactionBuilder {
        return copy(referenceInputStateAndRefs = referenceInputStateAndRefs + stateAndRef)
    }

    override fun addOutputState(contractState: ContractState): UtxoTransactionBuilder {
        return addOutputState(contractState, null)
    }

    override fun addOutputState(contractState: ContractState, encumbrance: Int?): UtxoTransactionBuilder {
        return copy(outputStates = outputStates + Pair(contractState, encumbrance))
    }

    override fun setTimeWindowUntil(until: Instant): UtxoTransactionBuilder {
        return copy(timeWindow = TimeWindowUntilImpl(until))
    }

    override fun setTimeWindowBetween(from: Instant, until: Instant): UtxoTransactionBuilder {
        return copy(timeWindow = TimeWindowBetweenImpl(from, until))
    }

    @Suspendable
    override fun sign(): UtxoSignedTransaction {
        TODO("Not yet implemented")
    }

    @Suspendable
    override fun sign(vararg signatories: PublicKey): UtxoSignedTransaction =
        sign(signatories.toList())

    @Suspendable
    override fun sign(signatories: Iterable<PublicKey>): UtxoSignedTransaction {
        require(signatories.toList().isNotEmpty()) {
            "At least one key needs to be provided in order to create a signed Transaction!"
        }
        verifyIfReady()
        val wireTransaction = buildWireTransaction()
        val signaturesWithMetadata = signatories.map {
            createTransactionSignature(
                signingService,
                serializationService,
                getCpiSummary(),
                wireTransaction.id,
                it
            )
        }
        return UtxoSignedTransactionImpl(
            serializationService,
            signingService,
            digitalSignatureVerificationService,
            wireTransaction,
            signaturesWithMetadata
        )
    }

    private fun verifyIfReady() {
        // TODO(CORE-7116 more verifications)
        // TODO(CORE-7116 metadata verifications: nulls, order of CPKs, at least one CPK?))

        // Notary is not null
        checkNotNull(notary) { "Adding Output states is not possible until the notary has been set!" }

        // TODO Input notaries same (and later or rotated) as notary

        // timeWindow is not null
        checkNotNull(timeWindow)

        // At least one input, or one output
        require(inputStateAndRefs.isNotEmpty() || outputStates.isNotEmpty()) {
            "At least one input or output state is required"
        }

        // TODO At least one required signer

        // TODO At least one command

        // TODO probably some more stuff we have to go look at C4 to remember
    }

    private fun buildWireTransaction(): WireTransaction {
        val componentGroupLists = calculateComponentGroupLists()

        val entropy = ByteArray(32)
        cipherSchemeMetadata.secureRandom.nextBytes(entropy)
        val privacySalt = PrivacySaltImpl(entropy)

        return WireTransaction(
            merkleTreeProvider,
            digestService,
            jsonMarshallingService,
            privacySalt,
            componentGroupLists
        )
    }

    @Suppress("ComplexMethod")
    private fun calculateComponentGroupLists(): List<List<ByteArray>> {
        val notaryGroup = listOf(
            notary,
            timeWindow,
            /*TODO notaryallowlist*/
        )

        val outputTransactionStates = outputStates.map{
            TransactionStateImpl(it.first, notary!!, it.second)
        }

        val outputsInfo = outputTransactionStates.map {
            UtxoOutputInfoComponent(
                it.encumbrance,
                notary!!,
                currentSandboxGroup.getEvolvableTag(it.contractStateType),
                currentSandboxGroup.getEvolvableTag(it.contractType)
            )
        }
        val commandsInfo = commands.map {
            listOf(
                "", // TODO signers
                currentSandboxGroup.getEvolvableTag(it.javaClass),
            )
        }

        return UtxoComponentGroup
            .values()
            .sorted()
            .map { componentGroupIndex ->
                when (componentGroupIndex) {
                    UtxoComponentGroup.METADATA ->
                        listOf(
                            jsonMarshallingService.format(transactionMetadata)
                                .toByteArray(Charsets.UTF_8)
                        ) // TODO(update with CORE-6890)
                    UtxoComponentGroup.NOTARY ->
                        notaryGroup.map { serializationService.serialize(it!!).bytes }

                    UtxoComponentGroup.OUTPUTS_INFO ->
                        outputsInfo.map { serializationService.serialize(it).bytes }

                    UtxoComponentGroup.COMMANDS_INFO ->
                        commandsInfo.map { serializationService.serialize(it).bytes }

                    UtxoComponentGroup.DATA_ATTACHMENTS ->
                        attachments.map { serializationService.serialize(it).bytes }

                    UtxoComponentGroup.INPUTS ->
                        inputStateAndRefs.map { serializationService.serialize(it.ref).bytes }

                    UtxoComponentGroup.OUTPUTS ->
                        outputTransactionStates.map { serializationService.serialize(it.contractState).bytes }

                    UtxoComponentGroup.COMMANDS ->
                        commands.map { serializationService.serialize(it).bytes }

                    UtxoComponentGroup.REFERENCES ->
                        referenceInputStateAndRefs.map { serializationService.serialize(it.ref).bytes }
                }
        }
    }

    @Suppress("ComplexMethod")
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is UtxoTransactionBuilderImpl) return false
        if (other.transactionMetadata != transactionMetadata) return false
        if (other.notary != notary) return false
        if (other.timeWindow != timeWindow) return false

        if (other.attachments.size != attachments.size) return false
        other.attachments.withIndex().all {
            it.value == attachments[it.index]
        } || return false

        if (other.commands.size != commands.size) return false
        other.commands.withIndex().all {
            it.value == commands[it.index]
        } || return false

        if (other.inputStateAndRefs.size != inputStateAndRefs.size) return false
        other.inputStateAndRefs.withIndex().all {
            it.value == inputStateAndRefs[it.index]
        } || return false

        if (other.referenceInputStateAndRefs.size != referenceInputStateAndRefs.size) return false
        other.referenceInputStateAndRefs.withIndex().all {
            it.value == referenceInputStateAndRefs[it.index]
        } || return false

        if (other.outputStates.size != outputStates.size) return false
        other.outputStates.withIndex().all {
            it.value == outputStates[it.index]
        } || return false

        if (other.signatories != signatories) return false

        return true
    }

    override fun hashCode(): Int {
        var result = transactionMetadata.hashCode()
        result = 31 * result + notary.hashCode()
        result = 31 * result + timeWindow.hashCode()
        result = 31 * result + attachments.hashCode()
        result = 31 * result + commands.hashCode()
        result = 31 * result + signatories.hashCode()
        result = 31 * result + inputStateAndRefs.hashCode()
        result = 31 * result + referenceInputStateAndRefs.hashCode()
        result = 31 * result + outputStates.hashCode()
        return result
    }
}

/**
 * TODO [CORE-7126] Fake values until we can get CPI information properly
 */
private fun getCpiSummary(): CordaPackageSummary =
    CordaPackageSummary(
        name = "CPI name",
        version = "CPI version",
        signerSummaryHash = SecureHash("SHA-256", "Fake-value".toByteArray()).toHexString(),
        fileChecksum = SecureHash("SHA-256", "Another-Fake-value".toByteArray()).toHexString()
    )