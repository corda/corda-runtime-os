package net.corda.ledger.consensual.flow.impl.transaction

import net.corda.ledger.common.data.transaction.TransactionBuilderInternal
import net.corda.ledger.consensual.data.transaction.ConsensualComponentGroup
import net.corda.ledger.consensual.flow.impl.transaction.factory.ConsensualSignedTransactionFactory
import net.corda.sandbox.SandboxGroup
import net.corda.v5.application.serialization.SerializationService
import net.corda.v5.base.annotations.Suspendable
import net.corda.v5.ledger.consensual.ConsensualState
import net.corda.v5.ledger.consensual.transaction.ConsensualSignedTransaction
import net.corda.v5.ledger.consensual.transaction.ConsensualTransactionBuilder
import java.security.PublicKey
import java.time.Instant
import java.util.Objects

// Is this still needed? TODO Create an AMQP serializer if we plan on sending transaction builders between virtual nodes
class ConsensualTransactionBuilderImpl(
    private val consensualSignedTransactionFactory: ConsensualSignedTransactionFactory,
    // cpi defines what type of signing/hashing is used (related to the digital signature signing and verification stuff)
    override val states: List<ConsensualState> = emptyList(),
) : ConsensualTransactionBuilder, TransactionBuilderInternal {

    override fun withStates(vararg states: ConsensualState): ConsensualTransactionBuilder =
        this.copy(states = this.states + states)

    @Suspendable
    override fun sign(): ConsensualSignedTransaction {
        TODO("Not yet implemented")
    }

    @Suspendable
    override fun sign(vararg signatories: PublicKey): ConsensualSignedTransaction =
        sign(signatories.toList())

    @Suspendable
    override fun sign(signatories: Iterable<PublicKey>): ConsensualSignedTransaction{
        require(signatories.toList().isNotEmpty()) {
            "At least one key needs to be provided in order to create a signed Transaction!"
        }
        return consensualSignedTransactionFactory.create(this, signatories)
    }

    override fun calculateComponentGroups(
        serializationService: SerializationService,
        metadataBytes: ByteArray,
        currentSandboxGroup: SandboxGroup
    ): List<List<ByteArray>> {
        // TODO(CORE-5982 more verifications)
        // TODO(CORE-5982 ? metadata verifications: nulls, order of CPKs, at least one CPK?))
        require(states.isNotEmpty()) { "At least one consensual state is required" }
        require(states.all { it.participants.isNotEmpty() }) { "All consensual states must have participants" }

        val requiredSigningKeys = states
            .flatMap { it.participants }
            .distinct()

        return ConsensualComponentGroup
            .values()
            .sorted()
            .map { componentGroupIndex ->
            when (componentGroupIndex) {
                ConsensualComponentGroup.METADATA ->
                    listOf(metadataBytes)
                ConsensualComponentGroup.TIMESTAMP ->
                    listOf(serializationService.serialize(Instant.now()).bytes)
                ConsensualComponentGroup.REQUIRED_SIGNING_KEYS ->
                    requiredSigningKeys.map { serializationService.serialize(it).bytes }
                ConsensualComponentGroup.OUTPUT_STATES ->
                    states.map { serializationService.serialize(it).bytes }
                ConsensualComponentGroup.OUTPUT_STATE_TYPES ->
                    states.map { serializationService.serialize(currentSandboxGroup.getEvolvableTag(it::class.java)).bytes }
            }
        }
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is ConsensualTransactionBuilderImpl) return false
        return other.states == states
    }

    override fun hashCode(): Int = Objects.hash(states)

    private fun copy(states: List<ConsensualState> = this.states): ConsensualTransactionBuilderImpl {
        return ConsensualTransactionBuilderImpl(
            consensualSignedTransactionFactory,
            states,
        )
    }
}
