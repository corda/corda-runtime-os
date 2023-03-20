package net.corda.simulator.runtime.ledger

import net.corda.crypto.core.SecureHashImpl
import net.corda.simulator.SimulatorConfiguration
import net.corda.simulator.entities.ConsensualTransactionEntity
import net.corda.simulator.entities.ConsensualTransactionSignatureEntity
import net.corda.simulator.runtime.serialization.BaseSerializationService
import net.corda.utilities.serialization.deserialize
import net.corda.v5.application.crypto.DigitalSignatureAndMetadata
import net.corda.v5.application.crypto.DigitalSignatureMetadata
import net.corda.v5.application.crypto.SigningService
import net.corda.v5.application.serialization.SerializationService
import net.corda.v5.crypto.SecureHash
import net.corda.v5.crypto.SignatureSpec
import net.corda.v5.ledger.common.transaction.TransactionMetadata
import net.corda.v5.ledger.consensual.ConsensualState
import net.corda.v5.ledger.consensual.transaction.ConsensualLedgerTransaction
import net.corda.v5.ledger.consensual.transaction.ConsensualSignedTransaction
import java.security.MessageDigest
import java.security.PublicKey
import java.time.Instant
import java.util.Objects

class ConsensualSignedTransactionBase(
    private val signatures: List<DigitalSignatureAndMetadata>,
    private val ledgerTransactionInfo: ConsensualStateLedgerInfo,
    private val signingService: SigningService,
    private val config: SimulatorConfiguration
) : ConsensualSignedTransaction {

    companion object {
        internal fun fromEntity(
            entity: ConsensualTransactionEntity,
            signingService: SigningService,
            serializer: SerializationService,
            config: SimulatorConfiguration
        ): ConsensualSignedTransaction {
            val ledgerTransactionInfo = ConsensualStateLedgerInfo(
                serializer.deserialize(
                    entity.stateData
                ), entity.timestamp
            )
            val signatures = entity.signatures.map {
                serializer.deserialize(it.signatureWithKey, DigitalSignatureAndMetadata::class.java)
            }
            return ConsensualSignedTransactionBase(
                signatures,
                ledgerTransactionInfo,
                signingService,
                config
            )
        }
    }

    internal fun toEntity(): ConsensualTransactionEntity {
        val serializer = BaseSerializationService()
        val transactionEntity = ConsensualTransactionEntity(
            // TODO consider adding `SecureHash.getBytes()`
            String((id as SecureHashImpl).bytes),
            serializer.serialize(ledgerTransaction.states).bytes,
            ledgerTransaction.timestamp
        )
        val signatureEntities = signatures.mapIndexed { index, signature ->
            val signatureWithKey = serializer.serialize(signature).bytes
            ConsensualTransactionSignatureEntity(
                transactionEntity,
                index,
                signatureWithKey,
                signature.metadata.timestamp
            )
        }
        transactionEntity.signatures.addAll(signatureEntities)
        return transactionEntity
    }

    private data class ConsensualLedgerTransactionBase(
        val ledgerTransactionInfo: ConsensualStateLedgerInfo,
        private val timestamp: Instant
    ) : ConsensualLedgerTransaction {

        val bytes: ByteArray by lazy {
            val serializer = BaseSerializationService()
            serializer.serialize(ledgerTransactionInfo).bytes
        }

        override fun getId(): SecureHash {
            val digest = MessageDigest.getInstance("SHA-256")
            return SecureHashImpl(digest.algorithm, digest.digest(bytes))
        }

        override fun getRequiredSignatories(): Set<PublicKey> {
            return ledgerTransactionInfo.requiredSigningKeys
        }

        override fun getTimestamp(): Instant {
            return timestamp
        }

        override fun getStates(): List<ConsensualState> {
            return ledgerTransactionInfo.states
        }
    }

    private val ledgerTransaction = ConsensualLedgerTransactionBase(
        ledgerTransactionInfo,
        ledgerTransactionInfo.timestamp
    )

    internal fun addSignature(
        publicKey: PublicKey,
        timestamp: Instant = config.clock.instant()
    ): ConsensualSignedTransactionBase {
        val signature = signWithMetadata(publicKey, timestamp)
        return addSignatures(listOf(signature))
    }

    internal fun addSignatures(signatures: List<DigitalSignatureAndMetadata>): ConsensualSignedTransactionBase {
        return ConsensualSignedTransactionBase(
            signatures = this.signatures.plus(signatures),
            ledgerTransactionInfo,
            signingService,
            config
        )
    }

    override fun toLedgerTransaction(): ConsensualLedgerTransaction = ledgerTransaction

    private fun signWithMetadata(key: PublicKey, timestamp: Instant): DigitalSignatureAndMetadata {
        val signature = signingService.sign(ledgerTransaction.bytes, key, SignatureSpec.ECDSA_SHA256)
        return DigitalSignatureAndMetadata(
            signature,
            DigitalSignatureMetadata(timestamp, SignatureSpec("dummySignatureName"), mapOf())
        )
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as ConsensualSignedTransactionBase

        return Objects.equals(signatures, other.signatures)
                && Objects.equals(ledgerTransactionInfo, other.ledgerTransactionInfo)
                && Objects.equals(config, other.config)
    }

    override fun hashCode(): Int {
        return Objects.hash(signatures, ledgerTransactionInfo, config)
    }

    override fun getId(): SecureHash {
        return ledgerTransaction.id
    }

    override fun getMetadata(): TransactionMetadata {
        TODO("Not yet implemented")
    }

    override fun getSignatures(): List<DigitalSignatureAndMetadata> {
        return signatures
    }
}
