package net.corda.ledger.consensual.impl.transactions

import net.corda.ledger.common.impl.transactions.PrivacySaltImpl
import net.corda.ledger.common.impl.transactions.SignedTransactionImpl
import net.corda.ledger.common.impl.transactions.TransactionMetaDataImpl
import net.corda.ledger.common.impl.transactions.WireTransactionImpl
import net.corda.v5.application.crypto.DigitalSignatureAndMetadata
import net.corda.v5.application.crypto.DigitalSignatureMetadata
import net.corda.v5.application.crypto.SigningService
import net.corda.v5.application.serialization.SerializationService
import net.corda.v5.crypto.DigestService
import net.corda.v5.crypto.DigitalSignature
import net.corda.v5.crypto.merkle.MerkleTreeFactory
import net.corda.v5.ledger.common.transactions.SignedTransaction
import net.corda.v5.ledger.common.transactions.TransactionMetaData
import net.corda.v5.ledger.common.transactions.TransactionMetaData.Companion.LEDGER_MODEL_KEY
import net.corda.v5.ledger.common.transactions.TransactionMetaData.Companion.LEDGER_VERSION_KEY
import net.corda.v5.ledger.common.transactions.WireTransaction
import net.corda.v5.ledger.consensual.ConsensualState
import net.corda.v5.ledger.consensual.transaction.ConsensualTransactionBuilder
import java.security.PublicKey
import java.security.SecureRandom
import java.time.Instant

class ConsensualTransactionBuilderImpl(
    override val merkleTreeFactory: MerkleTreeFactory,
    override val digestService: DigestService,
    override val secureRandom: SecureRandom,
    override val serializer: SerializationService,
    override val signingService: SigningService,

    override val timeStamp: Instant? = null,
    override val consensualStates: List<ConsensualState> = emptyList(),
) : ConsensualTransactionBuilder {

    private fun copy(
        timeStamp: Instant? = this.timeStamp,
        consensualStates: List<ConsensualState> = this.consensualStates
    ): ConsensualTransactionBuilderImpl {
        return ConsensualTransactionBuilderImpl(
            merkleTreeFactory, digestService, secureRandom, serializer, signingService,
            timeStamp, consensualStates,
        )
    }

    override fun withTimeStamp(timeStamp: Instant): ConsensualTransactionBuilder =
        this.copy(timeStamp = timeStamp)

    override fun withConsensualState(consensualState: ConsensualState): ConsensualTransactionBuilder =
        this.copy(consensualStates = consensualStates + consensualState)

    private fun calculateMetaData(): TransactionMetaData {
        return TransactionMetaDataImpl(mapOf(
            LEDGER_MODEL_KEY to ConsensualLedgerTransactionImpl::class.java,
            LEDGER_VERSION_KEY to TRANSACTION_META_DATA_CONSENSUAL_LEDGER_VERSION
            // TODO(CORE-5940 CPK identifier/etc)
        ))
    }

    private fun calculateComponentGroupLists(serializer: SerializationService): List<List<ByteArray>>
    {
        require(timeStamp != null){"Null timeStamp is not allowed"}

        val requiredSigningKeys = consensualStates //TODO: unique? ordering
            .map{it.participants}
            .flatten()
            .map{it.owningKey}

        val componentGroupLists = mutableListOf<List<ByteArray>>()
        for (componentGroupIndex in ConsensualComponentGroups.values()) {
            componentGroupLists += when (componentGroupIndex) {
                ConsensualComponentGroups.METADATA ->
                    listOf(serializer.serialize(calculateMetaData()).bytes)
                ConsensualComponentGroups.TIMESTAMP ->
                    listOf(serializer.serialize(timeStamp).bytes)
                ConsensualComponentGroups.REQUIRED_SIGNING_KEYS ->
                    requiredSigningKeys.map{serializer.serialize(it).bytes}
                ConsensualComponentGroups.OUTPUT_STATES ->
                    consensualStates.map{serializer.serialize(it).bytes}
                ConsensualComponentGroups.OUTPUT_STATE_TYPES ->
                    consensualStates.map{serializer.serialize(it::class.java.name).bytes}
            }
        }
        return componentGroupLists
    }

    override fun signInitial(publicKey: PublicKey): SignedTransaction {
        val wireTransaction = buildWireTransaction()
        // TODO(we just fake the signature for now...)
//        val signature = signingService.sign(wireTransaction.id.bytes, publicKey, SignatureSpec.RSA_SHA256)
        val signature = DigitalSignature.WithKey(publicKey, "0".toByteArray(), mapOf())
        val digitalSignatureMetadata = DigitalSignatureMetadata(Instant.now(), mapOf()) //TODO(populate this properly...)
        val signatureWithMetaData = DigitalSignatureAndMetadata(signature, digitalSignatureMetadata)
        return SignedTransactionImpl(wireTransaction, listOf(signatureWithMetaData))
    }

    private fun buildWireTransaction() : WireTransaction{
        // TODO(more verifications)
        // TODO(CORE-5940 ? metadata verifications: nulls, order of CPKs, at least one CPK?)
        require(consensualStates.isNotEmpty()){"At least one Consensual State is required"}
        require(consensualStates.all{it.participants.isNotEmpty()}){"All consensual states needs to have participants"}
        val componentGroupLists = calculateComponentGroupLists(serializer)

        val entropy = ByteArray(32)
        secureRandom.nextBytes(entropy)
        val privacySalt = PrivacySaltImpl(entropy)

        return WireTransactionImpl(
            merkleTreeFactory,
            digestService,
            privacySalt,
            componentGroupLists
        )
    }
}