package net.corda.simulator.runtime.ledger.utxo

import net.corda.ledger.utxo.data.state.EncumbranceGroupImpl
import net.corda.ledger.utxo.data.state.StateAndRefImpl
import net.corda.ledger.utxo.data.state.TransactionStateImpl
import net.corda.simulator.SimulatorConfiguration
import net.corda.simulator.entities.UtxoTransactionEntity
import net.corda.simulator.entities.UtxoTransactionOutputEntity
import net.corda.simulator.entities.UtxoTransactionOutputEntityId
import net.corda.simulator.entities.UtxoTransactionSignatureEntity
import net.corda.simulator.runtime.ledger.SimTransactionMetadata
import net.corda.simulator.runtime.serialization.BaseSerializationService
import net.corda.v5.application.crypto.DigitalSignatureAndMetadata
import net.corda.v5.application.crypto.DigitalSignatureMetadata
import net.corda.v5.application.crypto.SigningService
import net.corda.v5.application.persistence.PersistenceService
import net.corda.v5.application.serialization.SerializationService
import net.corda.v5.application.serialization.deserialize
import net.corda.v5.crypto.SecureHash
import net.corda.v5.crypto.SignatureSpec
import net.corda.v5.ledger.common.Party
import net.corda.v5.ledger.common.transaction.TransactionMetadata
import net.corda.v5.ledger.utxo.Command
import net.corda.v5.ledger.utxo.StateRef
import net.corda.v5.ledger.utxo.TimeWindow
import net.corda.v5.ledger.utxo.StateAndRef
import net.corda.v5.ledger.utxo.ContractState
import net.corda.v5.ledger.utxo.transaction.UtxoLedgerTransaction
import net.corda.v5.ledger.utxo.transaction.UtxoSignedTransaction
import java.security.PublicKey
import java.time.Instant
import java.util.Objects

@Suppress("LongParameterList")
class UtxoSignedTransactionBase(
    override val signatures: List<DigitalSignatureAndMetadata>,
    private val ledgerInfo: UtxoStateLedgerInfo,
    private val signingService: SigningService,
    private val serializer: SerializationService,
    private val persistenceService: PersistenceService,
    private val config : SimulatorConfiguration,
) : UtxoSignedTransaction {

    companion object {
        internal fun fromEntity(
            entity: UtxoTransactionEntity,
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
                    serializer.deserialize(entity.commandData),
                    serializer.deserialize(entity.inputData),
                    serializer.deserialize(entity.notaryData),
                    serializer.deserialize(entity.referenceStateDate),
                    serializer.deserialize(entity.signatoriesDate),
                    serializer.deserialize(entity.timeWindowDate),
                    serializer.deserialize(entity.outputData),
                    serializer.deserialize(entity.attachmentData)
                ),
                signingService,
                serializer,
                persistenceService,
                config
            )
        }
    }

    internal fun toEntity(): UtxoTransactionEntity{
        val serializer = BaseSerializationService()
        val transactionEntity = UtxoTransactionEntity(
            String(id.bytes),
            serializer.serialize(ledgerInfo.commands).bytes,
            serializer.serialize(ledgerInfo.inputStateRefs).bytes,
            serializer.serialize(ledgerInfo.notary).bytes,
            serializer.serialize(ledgerInfo.referenceStateRefs).bytes,
            serializer.serialize(ledgerInfo.signatories).bytes,
            serializer.serialize(ledgerInfo.timeWindow).bytes,
            serializer.serialize(ledgerInfo.outputStates).bytes,
            serializer.serialize(ledgerInfo.attachments).bytes
        )
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

    internal fun toOutputsEntity(keys: Set<PublicKey>) : List<UtxoTransactionOutputEntity> {
        val serializer = BaseSerializationService()
        val encumbranceGroupSizes =
            ledgerInfo.outputStates.mapNotNull { it.encumbranceTag }.groupingBy { it }.eachCount()
        val outputEntities = ledgerInfo.outputStates.mapIndexed{ index, contractStateAndTag ->
            val stateData = serializer.serialize(contractStateAndTag.contractState).bytes
            val encumbrance = contractStateAndTag.toTransactionState(notary,
                contractStateAndTag.encumbranceTag?.let{tag -> encumbranceGroupSizes[tag]}).encumbrance
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

        //Filter relevant outputs
        val relevantIndexes = this.outputStateAndRefs.withIndex().filter { (_, stateAndRef) ->
            val contract = stateAndRef.state.contractType.getConstructor().newInstance()
            contract.isRelevant(stateAndRef.state.contractState, keys)
        }.map { it.index }
        return outputEntities.filter { relevantIndexes.contains(it.index) }
    }

    override val metadata: TransactionMetadata
        get() = SimTransactionMetadata()

    override val notary: Party
        get() = ledgerInfo.notary

    private val ledgerTransaction =
        UtxoLedgerTransactionBase(
            ledgerInfo,
            getStateAndRef(ledgerInfo.inputStateRefs),
            getStateAndRef(ledgerInfo.referenceStateRefs)
        )

    override val id: SecureHash = ledgerTransaction.id

    override val outputStateAndRefs: List<StateAndRef<*>>
        get() {
            return ledgerTransaction.outputStateAndRefs
        }
    override val commands: List<Command>
        get() = ledgerInfo.commands
    override val inputStateRefs: List<StateRef>
        get() = ledgerInfo.inputStateRefs

    override val referenceStateRefs: List<StateRef>
        get() = ledgerInfo.referenceStateRefs
    override val signatories: List<PublicKey>
        get() = ledgerInfo.signatories
    override val timeWindow: TimeWindow
        get() = ledgerInfo.timeWindow

    override fun toLedgerTransaction(): UtxoLedgerTransaction {
        return ledgerTransaction
    }

    private fun getStateAndRef(stateRefs: List<StateRef>): List<StateAndRef<*>>{
        return stateRefs.map {
            val entity = persistenceService.find(
                UtxoTransactionOutputEntity::class.java,
                UtxoTransactionOutputEntityId(it.transactionId.toString(), it.index)
            ) ?: throw IllegalArgumentException("Cannot find transaction with transaction id: " +
                        String(it.transactionId.bytes))
            val contractState = serializer.deserialize<ContractState>(entity.stateData)
            val encumbrance = serializer
                .deserialize<List<EncumbranceGroupImpl>>(entity.encumbranceData).firstOrNull()
            val transactionState = TransactionStateImpl(contractState, notary, encumbrance)
            StateAndRefImpl(transactionState, it)
        }
    }

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
        val signature = signingService.sign(ledgerTransaction.bytes, key, SignatureSpec.ECDSA_SHA256)
        return DigitalSignatureAndMetadata(signature,
            DigitalSignatureMetadata(timestamp, SignatureSpec.ECDSA_SHA256, mapOf()))
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

}