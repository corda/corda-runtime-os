package net.corda.ledger.consensual.impl.transaction

import net.corda.ledger.common.impl.transaction.WireTransaction
import net.corda.v5.application.serialization.SerializationService
import net.corda.v5.crypto.SecureHash
import net.corda.v5.ledger.consensual.ConsensualState
import net.corda.v5.ledger.consensual.transaction.ConsensualLedgerTransaction
import java.security.PublicKey
import java.time.Instant

class ConsensualLedgerTransactionImpl(
    private val wireTransaction: WireTransaction,
    private val serializer: SerializationService
    ) : ConsensualLedgerTransaction {

    override val id: SecureHash
        get() = wireTransaction.id

    override val timestamp: Instant by lazy(LazyThreadSafetyMode.PUBLICATION) {
        val timeStampBytes = wireTransaction.getComponentGroupList(ConsensualComponentGroupEnum.TIMESTAMP.ordinal).first()
        serializer.deserialize(timeStampBytes, Instant::class.java)
    }
    override val requiredSigningKeys: Set<PublicKey> by lazy(LazyThreadSafetyMode.PUBLICATION) {
        wireTransaction
            .getComponentGroupList(ConsensualComponentGroupEnum.REQUIRED_SIGNING_KEYS.ordinal)
            .map{serializer.deserialize(it, PublicKey::class.java)}.toSet()
    }
    private val consensualStateTypes: List<String> by lazy(LazyThreadSafetyMode.PUBLICATION) {
        wireTransaction
            .getComponentGroupList(ConsensualComponentGroupEnum.OUTPUT_STATE_TYPES.ordinal)
            .map{serializer.deserialize(it, String::class.java)}
    }
    override val states: List<ConsensualState> by lazy(LazyThreadSafetyMode.PUBLICATION) {
        wireTransaction
            .getComponentGroupList(ConsensualComponentGroupEnum.OUTPUT_STATES.ordinal)
            .mapIndexed{
                index, state ->
            //@TODO(continue with net.corda.flow.application.sessions.FlowSessionImpl.deserializeReceivedPayload)
            serializer.deserialize(state, Class.forName(consensualStateTypes[index])) as ConsensualState
        }
    }

    /**
     * WIP CORE-5982
     */
    fun verify(){
        val requiredSigningKeysFromStates = states
            .map{it.participants}
            .flatten()
            .map{it.owningKey}
        require(requiredSigningKeys == requiredSigningKeysFromStates) {
            "Deserialized required signing keys from WireTx does not match with the ones derived from the states!"
        }
    }
}