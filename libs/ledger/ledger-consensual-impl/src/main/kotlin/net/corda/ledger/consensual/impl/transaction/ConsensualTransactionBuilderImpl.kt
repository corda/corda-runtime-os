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

@Suppress("LongParameterList")
class ConsensualTransactionBuilderImpl(
    private val merkleTreeFactory: MerkleTreeFactory,
    private val digestService: DigestService,
    private val secureRandom: SecureRandom,
    private val serializer: SerializationService,
    private val signingService: SigningService,
    override val states: List<ConsensualState> = emptyList(),
) : ConsensualTransactionBuilder {

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is ConsensualTransactionBuilderImpl) return false
        if (other.states.size != states.size) return false

        return other.states.withIndex().all{
            it.value == states[it.index]
        }
    }

    override fun hashCode(): Int = states.hashCode()

    private fun copy(
        states: List<ConsensualState> = this.states
    ): ConsensualTransactionBuilderImpl {
        return ConsensualTransactionBuilderImpl(
            merkleTreeFactory, digestService, secureRandom, serializer, signingService,
            states,
        )
    }

    override fun withStates(vararg states: ConsensualState): ConsensualTransactionBuilder =
        this.copy(states = this.states + states)

    private fun calculateMetaData(): TransactionMetaData {
        return TransactionMetaData(mapOf(
            LEDGER_MODEL_KEY to ConsensualLedgerTransactionImpl::class.java.canonicalName,
            LEDGER_VERSION_KEY to TRANSACTION_META_DATA_CONSENSUAL_LEDGER_VERSION
            // CORE-5940 set CPK identifier/etc
        ))
    }

    private fun calculateComponentGroupLists(serializer: SerializationService): List<List<ByteArray>>
    {
        val requiredSigningKeys = states
            .map{it.participants}
            .flatten()
            .map{it.owningKey}
            .distinct()

        val componentGroupLists = mutableListOf<List<ByteArray>>()
        for (componentGroupIndex in ConsensualComponentGroupEnum.values()) {
            componentGroupLists += when (componentGroupIndex) {
                ConsensualComponentGroupEnum.METADATA ->
                    listOf(serializer.serialize(calculateMetaData()).bytes)
                ConsensualComponentGroupEnum.TIMESTAMP ->
                    listOf(serializer.serialize(Instant.now()).bytes)
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
        // CORE-5091 we just fake the signature for now...
//        val signature = signingService.sign(wireTransaction.id.bytes, publicKey, SignatureSpec.RSA_SHA256)
        val signature = DigitalSignature.WithKey(publicKey, "0".toByteArray(), mapOf())
        val digitalSignatureMetadata = DigitalSignatureMetadata(Instant.now(), mapOf()) //CORE-5091 populate this properly...
        val signatureWithMetaData = DigitalSignatureAndMetadata(signature, digitalSignatureMetadata)
        return ConsensualSignedTransactionImpl(serializer, wireTransaction, listOf(signatureWithMetaData))
    }

    private fun buildWireTransaction() : WireTransaction{
        // CORE-5982 more verifications
        // CORE-5940 ? metadata verifications: nulls, order of CPKs, at least one CPK?)
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