package net.corda.simulator.runtime.ledger.utxo

import net.corda.ledger.utxo.data.state.StateAndRefImpl
import net.corda.ledger.utxo.data.state.TransactionStateImpl
import net.corda.simulator.SimulatorConfiguration
import net.corda.simulator.entities.UtxoTransactionEntity
import net.corda.simulator.entities.UtxoTransactionOutputEntity
import net.corda.simulator.entities.UtxoTransactionSignatureEntity
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
import net.corda.v5.ledger.utxo.ContractState
import net.corda.v5.ledger.utxo.StateAndRef
import net.corda.v5.ledger.utxo.transaction.UtxoLedgerTransaction
import net.corda.v5.ledger.utxo.transaction.UtxoSignedTransaction
import net.corda.v5.ledger.utxo.transaction.getOutputStates
import java.security.PublicKey
import java.time.Instant

@Suppress("LongParameterList")
class UtxoSignedTransactionBase(
    override val commands: List<Command>,
    override val inputStateRefs: List<StateRef>,
    override val notary: Party,
    override val referenceStateRefs: List<StateRef>,
    override val signatories: List<PublicKey>,
    override val signatures: List<DigitalSignatureAndMetadata>,
    override val timeWindow: TimeWindow,
    private val outputStates: List<ContractState>,
    private val attachments: List<SecureHash>,
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
            config: SimulatorConfiguration,
            persistenceService: PersistenceService
        ): UtxoSignedTransaction {

            val ledgerTx: UtxoLedgerTransactionBase = serializer.deserialize(
                entity.stateData
            )
            val signatures = entity.signatures.map {
                serializer.deserialize(it.signatureWithKey, DigitalSignatureAndMetadata::class.java)
            }
            return UtxoSignedTransactionBase(
                ledgerTx.commands,
                ledgerTx.inputStateRefs,
                ledgerTx.notary,
                ledgerTx.referenceStateRefs,
                ledgerTx.signatories,
                signatures,
                ledgerTx.timeWindow,
                ledgerTx.getOutputStates(),
                emptyList(),
                signingService,
                serializer,
                persistenceService,
                config
            )
        }
    }

    override val metadata: TransactionMetadata
        get() {
            TODO()
        }

    // TODO Populate Empty list
    private val ledgerTransaction =
        UtxoLedgerTransactionBase(
            emptyList(),
            commands,
            toInputStateAndRef(),
            inputStateRefs,
            emptyList(),
            referenceStateRefs,
            signatories,
            timeWindow,
            outputStates,
            notary
        )

    override val id: SecureHash = ledgerTransaction.id

    override val outputStateAndRefs: List<StateAndRef<*>>
        get() {
            return ledgerTransaction.outputStateAndRefs
        }

    override fun toLedgerTransaction(): UtxoLedgerTransaction {
        return ledgerTransaction
    }

    private fun toInputStateAndRef(): List<StateAndRef<*>>{
        return inputStateRefs.map {
            val entity = persistenceService.query("UtxoTransactionEntity.findByTransactionId",
                UtxoTransactionEntity::class.java)
                .setParameter("transactionId", String(it.transactionHash.bytes))
                .execute().firstOrNull()
                ?: throw IllegalArgumentException("Cannot find transaction with transaction id: " +
                         String(it.transactionHash.bytes))
            val tx = fromEntity(entity, signingService, serializer, config, persistenceService)
            val ts = TransactionStateImpl(tx.toLedgerTransaction().outputContractStates[it.index],
                notary, null)
            StateAndRefImpl(ts, it)
        }
    }

    internal fun addSignature(
        publicKeys: List<PublicKey>,
        timestamp: Instant = config.clock.instant()
    ): UtxoSignedTransactionBase {
        val myKeys = signingService.findMySigningKeys(publicKeys.toSet())
        val signatures = myKeys.values.filterNotNull().map { signWithMetadata(it, timestamp) }
        return addSignatures(signatures)
    }

    internal fun addSignatures(signatures: List<DigitalSignatureAndMetadata>): UtxoSignedTransactionBase {
        return UtxoSignedTransactionBase(
            commands, inputStateRefs, notary, referenceStateRefs, signatories,
            signatures = this.signatures.plus(signatures),
            timeWindow,
            outputStates,
            attachments,
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

    internal fun toEntity(): UtxoTransactionEntity{
        val serializer = BaseSerializationService()
        val transactionEntity = UtxoTransactionEntity(
            String(id.bytes),
            serializer.serialize(ledgerTransaction).bytes
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

    internal fun toOutputsEntity() : List<UtxoTransactionOutputEntity> {
        return outputStates.mapIndexed{ index, contractState ->
            UtxoTransactionOutputEntity(
                id.toString(),
                contractState::class.java.name,
                index,
                false
            )
        }
    }
}