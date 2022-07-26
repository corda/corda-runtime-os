package net.corda.ledger.consensual.impl.transactions

import net.corda.v5.application.serialization.SerializationService
import net.corda.v5.crypto.SecureHash
import net.corda.v5.ledger.common.transactions.PrivacySalt
import net.corda.v5.ledger.common.transactions.WireTransaction
import net.corda.v5.ledger.consensual.ConsensualState
import net.corda.v5.ledger.consensual.Party
import net.corda.v5.ledger.consensual.transaction.ConsensualLedgerTransaction
import net.corda.v5.ledger.consensual.transaction.ConsensualTransactionMetaData
import java.time.Instant

class ConsensualLedgerTransactionImpl(
    override val wireTransaction: WireTransaction,
    private val serializer: SerializationService
    ) : ConsensualLedgerTransaction {

    override val id: SecureHash
        get() = wireTransaction.id
    override val privacySalt: PrivacySalt
        get() = wireTransaction.privacySalt

    override val metadata: ConsensualTransactionMetaData by lazy(LazyThreadSafetyMode.PUBLICATION) {
        val metadataBytes = wireTransaction.getComponentGroupList(ConsensualComponentGroups.METADATA.ordinal).first()
        serializer.deserialize(metadataBytes, ConsensualTransactionMetaDataImpl::class.java)
    }
    override val timestamp: Instant by lazy(LazyThreadSafetyMode.PUBLICATION) {
        val timeStampBytes = wireTransaction.getComponentGroupList(ConsensualComponentGroups.TIMESTAMP.ordinal).first()
        serializer.deserialize(timeStampBytes, Instant::class.java)
    }
    override val requiredSigners: List<Party> by lazy(LazyThreadSafetyMode.PUBLICATION) {
        wireTransaction
            .getComponentGroupList(ConsensualComponentGroups.REQUIRED_SIGNERS.ordinal)
            .map{serializer.deserialize(it, Party::class.java)}
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