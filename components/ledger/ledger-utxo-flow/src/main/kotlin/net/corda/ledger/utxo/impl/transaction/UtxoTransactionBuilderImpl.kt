package net.corda.ledger.utxo.impl.transaction

import net.corda.ledger.common.data.transaction.CordaPackageSummary
import net.corda.ledger.common.data.transaction.PrivacySaltImpl
import net.corda.ledger.common.data.transaction.TransactionMetaData
import net.corda.ledger.common.data.transaction.WireTransaction
import net.corda.ledger.common.data.transaction.createTransactionSignature
import net.corda.ledger.utxo.impl.state.TransactionStateImpl
import net.corda.ledger.utxo.impl.timewindow.TimeWindowBetweenImpl
import net.corda.ledger.utxo.impl.timewindow.TimeWindowUntilImpl
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
import net.corda.v5.ledger.utxo.TransactionState
import net.corda.v5.ledger.utxo.transaction.UtxoSignedTransaction
import net.corda.v5.ledger.utxo.transaction.UtxoTransactionBuilder
import java.security.PublicKey
import java.time.Instant

data class UtxoTransactionBuilderImpl(
    private val cipherSchemeMetadata: CipherSchemeMetadata,
    private val digestService: DigestService,
    private val jsonMarshallingService: JsonMarshallingService,
    private val merkleTreeProvider: MerkleTreeProvider,
    private val serializationService: SerializationService,
    private val signingService: SigningService,
    private val digitalSignatureVerificationService: DigitalSignatureVerificationService,
    private val currentSandboxGroup: SandboxGroup,
    // cpi defines what type of signing/hashing is used (related to the digital signature signing and verification stuff)
    private val transactionMetaData: TransactionMetaData,

    override val notary: Party,
    private val timeWindow: TimeWindow,
    private val attachments: List<SecureHash> = emptyList(),
    private val commands: List<Command> = emptyList(),
    private val signatories: Set<PublicKey> = emptySet(),
    private val inputStateAndRefs: List<StateAndRef<*>> = emptyList(),
    private val referenceInputStateAndRefs: List<StateAndRef<*>> = emptyList(),
    private val outputTransactionStates: List<TransactionState<*>> = emptyList()
) : UtxoTransactionBuilder {


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
        val transactionState = TransactionStateImpl(contractState, notary, encumbrance)
        return copy(outputTransactionStates = outputTransactionStates + transactionState)
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
        val wireTransaction = buildWireTransaction()
        val signaturesWithMetaData = signatories.map {
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
            signaturesWithMetaData
        )
    }

    private fun buildWireTransaction(): WireTransaction {
        // TODO(CORE-5982-? more verifications)
        // TODO(CORE-5982-? metadata verifications: nulls, order of CPKs, at least one CPK?))
        require(inputStateAndRefs.isNotEmpty() || outputTransactionStates.isNotEmpty()) {
            "At least one input or output state is required"
        }
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

    private fun calculateComponentGroupLists(): List<List<ByteArray>> {
        val notaryGroup = listOf(
            notary,
            timeWindow,
            /*TODO: notaryallowlist*/
        )
        val outputsInfo = outputTransactionStates.map{
            UtxoOutputInfoComponent(
                it.encumbrance,
                notary,
                currentSandboxGroup.getEvolvableTag(it.contractStateType),
                currentSandboxGroup.getEvolvableTag(it.contractType)
            )
        }
        val commandsInfo = commands.map{ listOf(
            "", // TODO: signers
            currentSandboxGroup.getEvolvableTag(it.javaClass), // TODO: Type (CPKInfo + ClassName)
        )}

        val componentGroupLists = mutableListOf<List<ByteArray>>()
        for (componentGroupIndex in UtxoComponentGroup.values()) {
            componentGroupLists += when (componentGroupIndex) {
                UtxoComponentGroup.METADATA ->
                    listOf(
                        jsonMarshallingService.format(transactionMetaData)
                            .toByteArray(Charsets.UTF_8)
                    ) // TODO(update with CORE-6890)
                UtxoComponentGroup.NOTARY ->
                    notaryGroup.map { serializationService.serialize(it).bytes }
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
        return componentGroupLists
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is UtxoTransactionBuilderImpl) return false
        if (other.transactionMetaData != transactionMetaData) return false
        //todo add other fields
        if (other.outputTransactionStates.size != outputTransactionStates.size) return false

        return other.outputTransactionStates.withIndex().all {
            it.value == outputTransactionStates[it.index]
        }
    }

    //todo
    override fun hashCode(): Int = outputTransactionStates.hashCode()
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