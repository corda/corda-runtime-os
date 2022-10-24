package net.corda.ledger.consensual.data.transaction

import net.corda.ledger.common.data.transaction.WireTransaction
import net.corda.v5.application.serialization.SerializationService
import net.corda.v5.crypto.SecureHash
import net.corda.v5.ledger.consensual.ConsensualState
import net.corda.v5.ledger.consensual.transaction.ConsensualLedgerTransaction
import java.security.PublicKey
import java.time.Instant

// TODO Break dependency on implementation from outside this module (needs a factory)
class ConsensualLedgerTransactionImpl(
    private val wireTransaction: WireTransaction,
    private val serializationService: SerializationService
) : ConsensualLedgerTransaction {

    override fun equals(other: Any?): Boolean =
        (other === this) ||
                ((other is ConsensualLedgerTransactionImpl) &&
                        (other.wireTransaction == wireTransaction)
                        )

    override fun hashCode(): Int = wireTransaction.hashCode()

    override val id: SecureHash
        get() = wireTransaction.id

    override val timestamp: Instant by lazy(LazyThreadSafetyMode.PUBLICATION) {
        val timeStampBytes = wireTransaction.getComponentGroupList(ConsensualComponentGroupEnum.TIMESTAMP.ordinal).first()
        serializationService.deserialize(timeStampBytes, Instant::class.java)
    }
    override val requiredSigningKeys: Set<PublicKey> by lazy(LazyThreadSafetyMode.PUBLICATION) {
        wireTransaction
            .getComponentGroupList(ConsensualComponentGroupEnum.REQUIRED_SIGNING_KEYS.ordinal)
            .map { serializationService.deserialize(it, PublicKey::class.java) }.toSet()
    }
    private val consensualStateTypes: List<String> by lazy(LazyThreadSafetyMode.PUBLICATION) {
        wireTransaction
            .getComponentGroupList(ConsensualComponentGroupEnum.OUTPUT_STATE_TYPES.ordinal)
            .map { serializationService.deserialize(it, String::class.java) }
    }
    override val states: List<ConsensualState> by lazy(LazyThreadSafetyMode.PUBLICATION) {
        wireTransaction
            .getComponentGroupList(ConsensualComponentGroupEnum.OUTPUT_STATES.ordinal)
            .map { state ->
                serializationService.deserialize(state, ConsensualState::class.java)
            }
    }

    /**
     * WIP CORE-5982
     */
    fun verify() {
        val requiredSigningKeysFromStates = states
            .flatMap { it.participants }
        require(requiredSigningKeys == requiredSigningKeysFromStates) {
            "Deserialized required signing keys from WireTx do not match with the ones derived from the states!"
        }
    }
}