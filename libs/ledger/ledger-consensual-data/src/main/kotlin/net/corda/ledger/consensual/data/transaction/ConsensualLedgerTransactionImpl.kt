package net.corda.ledger.consensual.data.transaction

import net.corda.ledger.common.data.transaction.WireTransaction
import net.corda.v5.application.serialization.SerializationService
import net.corda.v5.application.serialization.deserialize
import net.corda.v5.crypto.SecureHash
import net.corda.v5.ledger.consensual.ConsensualState
import net.corda.v5.ledger.consensual.transaction.ConsensualLedgerTransaction
import java.security.PublicKey
import java.time.Instant

class ConsensualLedgerTransactionImpl(
    private val wireTransaction: WireTransaction,
    private val serializationService: SerializationService
) : ConsensualLedgerTransaction {

    override val id: SecureHash
        get() = wireTransaction.id

    override val timestamp: Instant by lazy(LazyThreadSafetyMode.PUBLICATION) {
        val timeStampBytes = wireTransaction.getComponentGroupList(ConsensualComponentGroup.TIMESTAMP.ordinal).first()
        serializationService.deserialize(timeStampBytes)
    }
    override val requiredSignatories: Set<PublicKey> by lazy(LazyThreadSafetyMode.PUBLICATION) {
        wireTransaction
            .getComponentGroupList(ConsensualComponentGroup.SIGNATORIES.ordinal)
            .map { serializationService.deserialize(it, PublicKey::class.java) }.toSet()
    }
    private val consensualStateTypes: List<String> by lazy(LazyThreadSafetyMode.PUBLICATION) {
        wireTransaction
            .getComponentGroupList(ConsensualComponentGroup.OUTPUT_STATE_TYPES.ordinal)
            .map { serializationService.deserialize(it) }
    }
    override val states: List<ConsensualState> by lazy(LazyThreadSafetyMode.PUBLICATION) {
        wireTransaction
            .getComponentGroupList(ConsensualComponentGroup.OUTPUT_STATES.ordinal)
            .map { serializationService.deserialize(it) }
    }

    override fun equals(other: Any?): Boolean {
        return (other === this) || ((other is ConsensualLedgerTransactionImpl) && (other.wireTransaction == wireTransaction))
    }

    override fun hashCode(): Int = wireTransaction.hashCode()

    init{
        check(wireTransaction.componentGroupLists[ConsensualComponentGroup.OUTPUT_STATES.ordinal].size ==
                wireTransaction.componentGroupLists[ConsensualComponentGroup.OUTPUT_STATE_TYPES.ordinal].size
        ) {
            "The length of the output states and output state types component groups needs to be the same."
        }
    }
}