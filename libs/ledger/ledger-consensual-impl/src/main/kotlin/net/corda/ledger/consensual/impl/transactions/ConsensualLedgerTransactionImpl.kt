package net.corda.ledger.consensual.impl.transactions

import net.corda.v5.application.serialization.SerializationService
import net.corda.v5.crypto.SecureHash
import net.corda.v5.ledger.common.transactions.PrivacySalt
import net.corda.v5.ledger.common.transactions.TransactionMetaData
import net.corda.v5.ledger.common.transactions.WireTransaction
import net.corda.v5.ledger.consensual.ConsensualState
import net.corda.v5.ledger.consensual.transaction.ConsensualLedgerTransaction
import java.security.PublicKey
import java.time.Instant

class ConsensualLedgerTransactionImpl(
    override val wireTransaction: WireTransaction,
    private val serializer: SerializationService
    ) : ConsensualLedgerTransaction {

    override val id: SecureHash
        get() = wireTransaction.id
    override val privacySalt: PrivacySalt
        get() = wireTransaction.privacySalt

    override val metadata: TransactionMetaData
        get() = wireTransaction.getMetadata(serializer)

    override val timestamp: Instant by lazy(LazyThreadSafetyMode.PUBLICATION) {
        val timeStampBytes = wireTransaction.getComponentGroupList(ConsensualComponentGroups.TIMESTAMP.ordinal).first()
        serializer.deserialize(timeStampBytes, Instant::class.java)
    }
    override val requiredSigningKeys: List<PublicKey> by lazy(LazyThreadSafetyMode.PUBLICATION) {
        wireTransaction
            .getComponentGroupList(ConsensualComponentGroups.REQUIRED_SIGNING_KEYS.ordinal)
            .map{serializer.deserialize(it, PublicKey::class.java)}
    }
    override val consensualStateTypes: List<String> by lazy(LazyThreadSafetyMode.PUBLICATION) {
        wireTransaction
            .getComponentGroupList(ConsensualComponentGroups.OUTPUT_STATE_TYPES.ordinal)
            .map{serializer.deserialize(it, String::class.java)}
    }
    override val consensualStates: List<ConsensualState> by lazy(LazyThreadSafetyMode.PUBLICATION) {
        wireTransaction
            .getComponentGroupList(ConsensualComponentGroups.OUTPUT_STATES.ordinal)
            .mapIndexed{
                index, state ->
            //@TODO(continue with net.corda.flow.application.sessions.FlowSessionImpl.deserializeReceivedPayload)
            serializer.deserialize(state, Class.forName(consensualStateTypes[index])) as ConsensualState
        }
    }
}