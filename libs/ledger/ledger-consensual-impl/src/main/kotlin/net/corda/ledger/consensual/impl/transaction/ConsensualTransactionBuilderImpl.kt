package net.corda.ledger.consensual.impl.transaction

import net.corda.ledger.common.impl.transaction.PrivacySaltImpl
import net.corda.ledger.common.impl.transaction.TransactionMetaData
import net.corda.ledger.common.impl.transaction.WireTransaction
import net.corda.ledger.common.internal.transaction.createTransactionSignature
import net.corda.ledger.consensual.impl.transaction.factory.getCpiSummary
import net.corda.v5.application.crypto.DigitalSignatureVerificationService
import net.corda.v5.application.crypto.SigningService
import net.corda.v5.application.marshalling.JsonMarshallingService
import net.corda.v5.application.serialization.SerializationService
import net.corda.v5.base.annotations.Suspendable
import net.corda.v5.cipher.suite.CipherSchemeMetadata
import net.corda.v5.cipher.suite.DigestService
import net.corda.v5.cipher.suite.merkle.MerkleTreeProvider
import net.corda.v5.ledger.consensual.ConsensualState
import net.corda.v5.ledger.consensual.transaction.ConsensualSignedTransaction
import net.corda.v5.ledger.consensual.transaction.ConsensualTransactionBuilder
import java.security.PublicKey
import java.time.Instant

// TODO Create an AMQP serializer if we plan on sending transaction builders between virtual nodes
@Suppress("LongParameterList")
class ConsensualTransactionBuilderImpl(
    private val cipherSchemeMetadata: CipherSchemeMetadata,
    private val digestService: DigestService,
    private val jsonMarshallingService: JsonMarshallingService,
    private val merkleTreeProvider: MerkleTreeProvider,
    private val serializationService: SerializationService,
    private val signingService: SigningService,
    private val digitalSignatureVerificationService: DigitalSignatureVerificationService,
    // cpi defines what type of signing/hashing is used (related to the digital signature signing and verification stuff)
    private val transactionMetaData: TransactionMetaData,
    override val states: List<ConsensualState> = emptyList(),
) : ConsensualTransactionBuilder {

    override fun withStates(vararg states: ConsensualState): ConsensualTransactionBuilder =
        this.copy(states = this.states + states)

    @Suspendable
    override fun sign(publicKey: PublicKey): ConsensualSignedTransaction {
        val wireTransaction = buildWireTransaction()
        val signatureWithMetaData = createTransactionSignature(
            signingService,
            serializationService,
            getCpiSummary(),
            wireTransaction.id,
            publicKey
        )
        return ConsensualSignedTransactionImpl(
            serializationService,
            signingService,
            digitalSignatureVerificationService,
            wireTransaction,
            listOf(signatureWithMetaData)
        )
    }

    private fun buildWireTransaction(): WireTransaction {
        // TODO(CORE-5982 more verifications)
        // TODO(CORE-5982 ? metadata verifications: nulls, order of CPKs, at least one CPK?))
        require(states.isNotEmpty()) { "At least one consensual state is required" }
        require(states.all { it.participants.isNotEmpty() }) { "All consensual states must have participants" }
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
        val requiredSigningKeys = states
            .map { it.participants }
            .flatten()
            .map { it.owningKey }
            .distinct()

        val componentGroupLists = mutableListOf<List<ByteArray>>()
        for (componentGroupIndex in ConsensualComponentGroupEnum.values()) {
            componentGroupLists += when (componentGroupIndex) {
                ConsensualComponentGroupEnum.METADATA ->
                    listOf(
                        jsonMarshallingService.format(transactionMetaData)
                            .toByteArray(Charsets.UTF_8)
                    ) // TODO(update with CORE-6890)
                ConsensualComponentGroupEnum.TIMESTAMP ->
                    listOf(serializationService.serialize(Instant.now()).bytes)
                ConsensualComponentGroupEnum.REQUIRED_SIGNING_KEYS ->
                    requiredSigningKeys.map { serializationService.serialize(it).bytes }
                ConsensualComponentGroupEnum.OUTPUT_STATES ->
                    states.map { serializationService.serialize(it).bytes }
                ConsensualComponentGroupEnum.OUTPUT_STATE_TYPES ->
                    states.map { serializationService.serialize(it::class.java.name).bytes }
            }
        }
        return componentGroupLists
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is ConsensualTransactionBuilderImpl) return false
        if (other.transactionMetaData != transactionMetaData) return false
        if (other.states.size != states.size) return false

        return other.states.withIndex().all {
            it.value == states[it.index]
        }
    }

    override fun hashCode(): Int = states.hashCode()

    private fun copy(states: List<ConsensualState> = this.states): ConsensualTransactionBuilderImpl {
        return ConsensualTransactionBuilderImpl(
            cipherSchemeMetadata,
            digestService,
            jsonMarshallingService,
            merkleTreeProvider,
            serializationService,
            signingService,
            digitalSignatureVerificationService,
            transactionMetaData,
            states,
        )
    }
}
