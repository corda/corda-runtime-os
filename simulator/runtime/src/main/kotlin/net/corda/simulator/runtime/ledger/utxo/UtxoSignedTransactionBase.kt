package net.corda.simulator.runtime.ledger.utxo

import net.corda.crypto.cipher.suite.SignatureSpecs
import net.corda.crypto.core.bytes
import net.corda.simulator.SimulatorConfiguration
import net.corda.simulator.entities.UtxoTransactionEntity
import net.corda.simulator.entities.UtxoTransactionOutputEntity
import net.corda.simulator.entities.UtxoTransactionOutputEntityId
import net.corda.simulator.entities.UtxoTransactionSignatureEntity
import net.corda.simulator.runtime.ledger.consensual.SimTransactionMetadata
import net.corda.simulator.runtime.serialization.BaseSerializationService
import net.corda.v5.application.crypto.DigitalSignatureAndMetadata
import net.corda.v5.application.crypto.DigitalSignatureMetadata
import net.corda.v5.application.crypto.SigningService
import net.corda.v5.application.persistence.PersistenceService
import net.corda.v5.application.serialization.SerializationService
import net.corda.v5.base.annotations.CordaSerializable
import net.corda.v5.base.types.MemberX500Name
import net.corda.v5.crypto.CompositeKey
import net.corda.v5.crypto.SecureHash
import net.corda.v5.ledger.common.transaction.TransactionMetadata
import net.corda.v5.ledger.utxo.Command
import net.corda.v5.ledger.utxo.ContractState
import net.corda.v5.ledger.utxo.EncumbranceGroup
import net.corda.v5.ledger.utxo.StateAndRef
import net.corda.v5.ledger.utxo.StateRef
import net.corda.v5.ledger.utxo.TimeWindow
import net.corda.v5.ledger.utxo.transaction.UtxoLedgerTransaction
import net.corda.v5.ledger.utxo.transaction.UtxoSignedTransaction
import net.corda.v5.membership.NotaryInfo
import java.security.PublicKey
import java.time.Instant
import java.util.Objects

/**
 * Simulator implementation of [UtxoSignedTransaction]
 */
@Suppress("LongParameterList", "TooManyFunctions")
@CordaSerializable
class UtxoSignedTransactionBase(
    private val signatures: List<DigitalSignatureAndMetadata>,
    private val ledgerInfo: UtxoStateLedgerInfo,
    private val signingService: SigningService,
    private val serializer: SerializationService,
    private val persistenceService: PersistenceService,
    private val config : SimulatorConfiguration,
) : UtxoSignedTransaction {

    private val ledgerTransaction =
        UtxoLedgerTransactionBase(
            ledgerInfo,
            getStateAndRef(ledgerInfo.inputStateRefs),
            getStateAndRef(ledgerInfo.referenceStateRefs)
        )


    companion object {

        /**
         * Deserialize each transaction components to creates a UTXO Signed Transaction from a tx entity
         */
        @Suppress("UNCHECKED_CAST")
        internal fun fromEntity(
            entity: UtxoTransactionEntity,
            notaryInfo: NotaryInfo,
            signingService: SigningService,
            serializer: SerializationService,
            persistenceService: PersistenceService,
            config: SimulatorConfiguration
        ): UtxoSignedTransaction {

            val signatures = entity.signatures.map {
                serializer.deserialize(it.signatureWithKey, DigitalSignatureAndMetadata::class.java)
            }
            return UtxoSignedTransactionBase(
                signatures,
                UtxoStateLedgerInfo(
                    serializer.deserialize(entity.commandData, List::class.java) as List<Command>,
                    serializer.deserialize(entity.inputData, List::class.java) as List<StateRef>,
                    serializer.deserialize(entity.referenceStateDate, List::class.java) as List<StateRef>,
                    serializer.deserialize(entity.signatoriesData, List::class.java) as List<PublicKey>,
                    serializer.deserialize(entity.timeWindowData, TimeWindow::class.java),
                    serializer.deserialize(entity.outputData, List::class.java) as List<ContractStateAndEncumbranceTag>,
                    serializer.deserialize(entity.attachmentData, List::class.java) as List<SecureHash>,
                    notaryInfo.name,
                    notaryInfo.publicKey
                ),
                signingService,
                serializer,
                persistenceService,
                config
            )
        }
    }

    /**
     * Converts signed transaction to entity
     */
    internal fun toEntity(): UtxoTransactionEntity{
        val serializer = BaseSerializationService()
        //Serialize individual ledger components
        val transactionEntity = UtxoTransactionEntity(
            String(id.bytes),
            serializer.serialize(ledgerInfo.commands).bytes,
            serializer.serialize(ledgerInfo.inputStateRefs).bytes,
            serializer.serialize(ledgerInfo.referenceStateRefs).bytes,
            serializer.serialize(ledgerInfo.signatories).bytes,
            serializer.serialize(ledgerInfo.timeWindow).bytes,
            serializer.serialize(ledgerInfo.outputStates).bytes,
            serializer.serialize(ledgerInfo.attachments).bytes
        )

        // And signature entity to tx entity
        val signatureEntities = signatures.mapIndexed { index, signature ->
            val signatureWithKey = serializer.serialize(signature).bytes
            UtxoTransactionSignatureEntity(
                transactionEntity,
                index,
                signatureWithKey,
                signature.metadata.timestamp
            )
        }

        transactionEntity.signatures.addAll(signatureEntities)
        return transactionEntity
    }

    /**
     * Builds output entity for outputs generated by the transaction
     *
     * @param keys A set of [PublicKey] to check and filter of relevant outputs
     */
    internal fun toOutputsEntity(keys: Set<PublicKey>) : List<UtxoTransactionOutputEntity> {
        val serializer = BaseSerializationService()
        val encumbranceGroupSizes =
            ledgerInfo.outputStates.mapNotNull { it.encumbranceTag }.groupingBy { it }.eachCount()
        val outputEntities = ledgerInfo.outputStates.mapIndexed{ index, contractStateAndTag ->
            val stateData = serializer.serialize(contractStateAndTag.contractState).bytes
            val encumbrance = contractStateAndTag.toTransactionState(notaryName, notaryKey,
                contractStateAndTag.encumbranceTag?.let{tag -> encumbranceGroupSizes[tag]}).encumbranceGroup
            val encumbranceData = serializer.serialize(listOf(encumbrance)).bytes
            UtxoTransactionOutputEntity(
                id.toString(),
                contractStateAndTag.contractState::class.java.name,
                encumbranceData,
                stateData,
                index,
                false
            )
        }

        //Filter relevant outputs, based on ledger keys
        val relevantIndexes = this.outputStateAndRefs.withIndex().filter { (_, stateAndRef) ->
            stateAndRef.state.contractState.participants.stream().anyMatch { key: PublicKey? ->
                isKeyInSet(key!!, keys)
            }
        }.map { it.index }
        return outputEntities.filter { relevantIndexes.contains(it.index) }
    }

    /**
     * Checks if a particular key is present in a list of keys
     *
     * @param key The [PublicKey] to be serched
     * @param otherKeys The list of [PublicKey] to search for the provided key
     */
    private fun isKeyInSet(key: PublicKey, otherKeys: Iterable<PublicKey>): Boolean {
        if (key is CompositeKey) {
            val leafKeys = key.leafKeys
            for (otherKey in otherKeys) {
                if (leafKeys.contains(otherKey)) return true
            }
        } else {
            for (otherKey in otherKeys) {
                if (otherKey == key) return true
            }
        }
        return false
    }

    override fun toLedgerTransaction(): UtxoLedgerTransaction {
        return ledgerTransaction
    }

    /**
     * Convert StateRef to StateAndRef. Queries the [UtxoTransactionOutputEntity] for each txId and index
     * (from [StateRef]) and deserializes the contractStateData and encumbranceData for each
     * [UtxoTransactionOutputEntity] to finally build the [StateAndRef]
     *
     * @param stateRefs A list of [StateRef] to be converted to [StateAndRef]
     */
    private fun getStateAndRef(stateRefs: List<StateRef>): List<StateAndRef<*>>{
        return stateRefs.map {
            val entity = persistenceService.find(
                UtxoTransactionOutputEntity::class.java,
                UtxoTransactionOutputEntityId(it.transactionId.toString(), it.index)
            ) ?: throw IllegalArgumentException("Cannot find transaction with transaction id: " +
                        String(it.transactionId.bytes))
            val contractState = serializer.deserialize(entity.stateData, ContractState::class.java)
            val encumbrance = serializer
                .deserialize(entity.encumbranceData, List::class.java).firstOrNull()
            val transactionState = SimTransactionState(
                contractState, notaryName, notaryKey, encumbrance as? EncumbranceGroup)
            SimStateAndRef(transactionState, it)
        }
    }

    /**
     * Adds signatures to a [UtxoSignedTransaction] for a list of provided [PublicKey]
     *
     * @param publicKeys The list of [PublicKey] to sign the transaction
     * @param timestamp for the signature
     */
    internal fun addSignatures(
        publicKeys: List<PublicKey>,
        timestamp: Instant = config.clock.instant()
    ): UtxoSignedTransactionBase {
        val myKeys = signingService.findMySigningKeys(publicKeys.toSet())
        val signatures = myKeys.values.filterNotNull().map { signWithMetadata(it, timestamp) }
        return addSignatureAndMetadata(signatures)
    }

    internal fun addSignatureAndMetadata(signatures: List<DigitalSignatureAndMetadata>): UtxoSignedTransactionBase {
        return UtxoSignedTransactionBase(
            signatures = this.signatures.plus(signatures),
            ledgerInfo,
            signingService,
            serializer,
            persistenceService,
            config
        )
    }

    private fun signWithMetadata(key: PublicKey, timestamp: Instant) : DigitalSignatureAndMetadata {
        val signature = signingService.sign(ledgerTransaction.bytes, key, SignatureSpecs.ECDSA_SHA256)
        return DigitalSignatureAndMetadata(signature,
            DigitalSignatureMetadata(timestamp, SignatureSpecs.ECDSA_SHA256, mapOf()))
    }

    override fun equals(other: Any?): Boolean {
        if(this === other) return true
        if (javaClass != other?.javaClass) return false
        other as UtxoSignedTransactionBase
        return this.id == other.id
    }

    override fun hashCode(): Int {
        return Objects.hash(id)
    }

    override fun getId(): SecureHash {
        return ledgerTransaction.id
    }

    override fun getMetadata(): TransactionMetadata {
        return SimTransactionMetadata()
    }

    override fun getSignatures(): List<DigitalSignatureAndMetadata> {
        return signatures
    }

    override fun getInputStateRefs(): List<StateRef> {
        return ledgerTransaction.inputStateRefs
    }

    override fun getReferenceStateRefs(): List<StateRef> {
        return ledgerTransaction.referenceStateRefs
    }

    override fun getOutputStateAndRefs(): List<StateAndRef<*>> {
        return ledgerTransaction.outputStateAndRefs
    }

    override fun getNotaryName(): MemberX500Name {
        return ledgerInfo.notaryName
    }

    override fun getNotaryKey(): PublicKey {
        return ledgerInfo.notaryKey
    }

    override fun getTimeWindow(): TimeWindow {
        return  ledgerInfo.timeWindow
    }

    override fun getCommands(): List<Command> {
        return ledgerInfo.commands
    }

    override fun getSignatories(): List<PublicKey> {
        return ledgerInfo.signatories
    }

}