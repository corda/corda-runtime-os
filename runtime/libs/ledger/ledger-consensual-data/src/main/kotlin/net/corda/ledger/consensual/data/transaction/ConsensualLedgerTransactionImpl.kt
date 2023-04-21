package net.corda.ledger.consensual.data.transaction

import net.corda.ledger.common.data.transaction.WireTransaction
import net.corda.ledger.consensual.data.transaction.verifier.verifyMetadata
import net.corda.utilities.serialization.deserialize
import net.corda.v5.application.serialization.SerializationService
import net.corda.v5.crypto.SecureHash
import net.corda.v5.ledger.consensual.ConsensualState
import net.corda.v5.ledger.consensual.transaction.ConsensualLedgerTransaction
import java.security.PublicKey
import java.time.Instant

class ConsensualLedgerTransactionImpl(
    private val wireTransaction: WireTransaction,
    private val serializationService: SerializationService
) : ConsensualLedgerTransaction {

    init {
        verifyMetadata(wireTransaction.metadata)
        check(
            wireTransaction.componentGroupLists[ConsensualComponentGroup.OUTPUT_STATES.ordinal].size ==
                    wireTransaction.componentGroupLists[ConsensualComponentGroup.OUTPUT_STATE_TYPES.ordinal].size
        ) {
            "The length of the output states and output state types component groups needs to be the same."
        }
    }

    override fun equals(other: Any?): Boolean {
        return (other === this) || ((other is ConsensualLedgerTransactionImpl) && (other.wireTransaction == wireTransaction))
    }

    override fun hashCode(): Int = wireTransaction.hashCode()

    override fun toString(): String {
        return "ConsensualLedgerTransactionImpl(id=$id, requiredSignatories=$requiredSignatories, wireTransaction=$wireTransaction)"
    }

    override fun getId(): SecureHash {
        return wireTransaction.id
    }

    override fun getRequiredSignatories(): Set<PublicKey> {
        return wireTransaction
            .getComponentGroupList(ConsensualComponentGroup.SIGNATORIES.ordinal)
            .map { serializationService.deserialize(it, PublicKey::class.java) }.toSet()
    }

    override fun getTimestamp(): Instant {
        val timeStampBytes = wireTransaction.getComponentGroupList(ConsensualComponentGroup.TIMESTAMP.ordinal).first()
        return serializationService.deserialize(timeStampBytes)
    }

    override fun getStates(): List<ConsensualState> {
        return wireTransaction
            .getComponentGroupList(ConsensualComponentGroup.OUTPUT_STATES.ordinal)
            .map { serializationService.deserialize(it) }
    }
}
