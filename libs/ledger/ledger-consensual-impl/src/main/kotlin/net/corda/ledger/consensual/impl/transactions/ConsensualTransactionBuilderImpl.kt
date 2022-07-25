package net.corda.ledger.consensual.impl.transactions

import net.corda.ledger.common.impl.transactions.PrivacySaltImpl
import net.corda.v5.application.serialization.SerializationService
import net.corda.v5.crypto.DigestService
import net.corda.v5.crypto.merkle.MerkleTreeFactory
import net.corda.v5.ledger.consensual.ConsensualState
import net.corda.v5.ledger.consensual.Party
import net.corda.v5.ledger.consensual.transaction.ConsensualTransactionBuilder
import net.corda.v5.ledger.consensual.transaction.ConsensualTransactionMetaData
import net.corda.v5.ledger.consensual.transaction.ConsensualWireTransaction
import java.security.PublicKey
import java.security.SecureRandom
import java.time.Instant

class ConsensualTransactionBuilderImpl(
    override val metadata: ConsensualTransactionMetaData? = null,
    override val timeStamp: Instant? = null,
    override val consensualStates: List<ConsensualState> = emptyList()
) : ConsensualTransactionBuilder {

    private fun copy(
        metadata: ConsensualTransactionMetaData? = this.metadata,
        timeStamp: Instant? = this.timeStamp,
        consensualStates: List<ConsensualState> = this.consensualStates

    ): ConsensualTransactionBuilderImpl {
        return ConsensualTransactionBuilderImpl(
            metadata,
            timeStamp,
            consensualStates,
        )
    }

    override fun withMetadata(metadata: ConsensualTransactionMetaData): ConsensualTransactionBuilder =
        this.copy(metadata = metadata)

    override fun withTimeStamp(timeStamp: Instant): ConsensualTransactionBuilder =
        this.copy(timeStamp = timeStamp)

    override fun withConsensualState(consensualState: ConsensualState): ConsensualTransactionBuilder =
        this.copy(consensualStates = consensualStates + consensualState)

    override fun build(
        merkleTreeFactory: MerkleTreeFactory,
        digestService: DigestService,
        secureRandom: SecureRandom,
        serializer: SerializationService
    ): ConsensualWireTransaction {
        // TODO(more verifications)
        // TODO(metadata verifications: nulls, order of CPKs, at least one CPK?)
        require(metadata != null){"Null metadata is not allowed"}
        require(timeStamp != null){"Null timeStamp is not allowed"}
        require(consensualStates.isNotEmpty()){"At least one Consensual State is required"}
        require(consensualStates.all{it.participants.isNotEmpty()}){"All consensual states needs to have participants"}

        val entropy = ByteArray(32)
        secureRandom.nextBytes(entropy)
        val privacySalt = PrivacySaltImpl(entropy)
        val requiredSigners = consensualStates.map{it.participants}.flatten()
        return ConsensualWireTransactionImpl(
            merkleTreeFactory,
            digestService,
            privacySalt,
            serializer.serialize(metadata).bytes,
            serializer.serialize(timeStamp).bytes,
            requiredSigners.map{serializer.serialize(it).bytes},
            consensualStates.map{serializer.serialize(it).bytes},
            consensualStates.map{serializer.serialize(it::class.java.name).bytes}
        )
    }
}