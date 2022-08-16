package net.corda.ledger.consensual.impl.transaction

import net.corda.ledger.common.impl.transaction.PrivacySaltImpl
import net.corda.ledger.common.impl.transaction.TransactionMetaData
import net.corda.ledger.common.impl.transaction.TransactionMetaData.Companion.LEDGER_MODEL_KEY
import net.corda.ledger.common.impl.transaction.TransactionMetaData.Companion.LEDGER_VERSION_KEY
import net.corda.ledger.common.impl.transaction.WireTransaction
import net.corda.v5.application.crypto.DigitalSignatureAndMetadata
import net.corda.v5.application.crypto.DigitalSignatureMetadata
import net.corda.v5.application.crypto.SigningService
import net.corda.v5.application.serialization.SerializationService
import net.corda.v5.crypto.DigestService
import net.corda.v5.crypto.DigitalSignature
import net.corda.v5.crypto.merkle.MerkleTreeFactory
import net.corda.v5.ledger.consensual.ConsensualState
import net.corda.v5.ledger.consensual.transaction.ConsensualSignedTransaction
import net.corda.v5.ledger.consensual.transaction.ConsensualTransactionBuilder
import java.security.PublicKey
import java.security.SecureRandom
import java.time.Instant

class ConsensualTransactionBuilderImpl(
    private val merkleTreeFactory: MerkleTreeFactory,
    private val digestService: DigestService,
    private val secureRandom: SecureRandom,
    private val serializer: SerializationService,
    private val signingService: SigningService,
    override val timestamp: Instant? = null,
    override val states: List<ConsensualState> = emptyList(),
) : ConsensualTransactionBuilder {

    private fun copy(
        timeStamp: Instant? = this.timestamp,
        states: List<ConsensualState> = this.states
    ): ConsensualTransactionBuilderImpl {
        return ConsensualTransactionBuilderImpl(
            merkleTreeFactory, digestService, secureRandom, serializer, signingService,
            timeStamp, states,
        )
    }

    override fun withTimestamp(timestamp: Instant): ConsensualTransactionBuilder =
        this.copy(timeStamp = timestamp)

    override fun withStates(vararg states: ConsensualState): ConsensualTransactionBuilder =
        this.copy(states = this.states + states)

    private fun calculateMetaData(): TransactionMetaData {
        return TransactionMetaData(mapOf(
            LEDGER_MODEL_KEY to ConsensualLedgerTransactionImpl::class.java.canonicalName,
            LEDGER_VERSION_KEY to TRANSACTION_META_DATA_CONSENSUAL_LEDGER_VERSION
            // TODO(CORE-5940 CPK identifier/etc)
        ))
    }

    private fun calculateComponentGroupLists(serializer: SerializationService): List<List<ByteArray>>
    {
        require(timestamp != null){"Null timeStamp is not allowed"}

        val requiredSigningKeys = states //TODO: unique? ordering
            .map{it.participants}
            .flatten()
            .map{it.owningKey}

        val componentGroupLists = mutableListOf<List<ByteArray>>()
        for (componentGroupIndex in ConsensualComponentGroupEnum.values()) {
            componentGroupLists += when (componentGroupIndex) {
                ConsensualComponentGroupEnum.METADATA ->
                    listOf(serializer.serialize(calculateMetaData()).bytes)
                ConsensualComponentGroupEnum.TIMESTAMP ->
                    listOf(serializer.serialize(timestamp).bytes)
                ConsensualComponentGroupEnum.REQUIRED_SIGNING_KEYS ->
                    requiredSigningKeys.map{serializer.serialize(it).bytes}
                ConsensualComponentGroupEnum.OUTPUT_STATES ->
                    states.map{serializer.serialize(it).bytes}
                ConsensualComponentGroupEnum.OUTPUT_STATE_TYPES ->
                    states.map{serializer.serialize(it::class.java.name).bytes}
            }
        }
        return componentGroupLists
    }

    override fun signInitial(publicKey: PublicKey): ConsensualSignedTransaction {
        val wireTransaction = buildWireTransaction()
        // TODO(we just fake the signature for now...)
//        val signature = signingService.sign(wireTransaction.id.bytes, publicKey, SignatureSpec.RSA_SHA256)
        val signature = DigitalSignature.WithKey(publicKey, "0".toByteArray(), mapOf())
        val digitalSignatureMetadata = DigitalSignatureMetadata(Instant.now(), mapOf()) //TODO(populate this properly...)
        val signatureWithMetaData = DigitalSignatureAndMetadata(signature, digitalSignatureMetadata)
        return ConsensualSignedTransactionImpl(wireTransaction, listOf(signatureWithMetaData))
    }

    private fun buildWireTransaction() : WireTransaction{
        // TODO(more verifications)
        // TODO(CORE-5940 ? metadata verifications: nulls, order of CPKs, at least one CPK?)
        require(states.isNotEmpty()){"At least one Consensual State is required"}
        require(states.all{it.participants.isNotEmpty()}){"All consensual states needs to have participants"}
        val componentGroupLists = calculateComponentGroupLists(serializer)

        val entropy = ByteArray(32)
        secureRandom.nextBytes(entropy)
        val privacySalt = PrivacySaltImpl(entropy)

        return WireTransaction(
            merkleTreeFactory,
            digestService,
            privacySalt,
            componentGroupLists
        )
    }
}