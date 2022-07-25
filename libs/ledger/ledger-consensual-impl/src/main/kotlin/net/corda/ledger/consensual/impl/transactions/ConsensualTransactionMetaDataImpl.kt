package net.corda.ledger.consensual.impl.transactions

import net.corda.v5.ledger.common.transactions.CpkIdentifier
import net.corda.v5.ledger.consensual.transaction.ConsensualTransactionMetaData

class ConsensualTransactionMetaDataImpl(
    override val ledgerModel: String,
    override val ledgerVersion: String,
    override val cpkIdentifiers: List<CpkIdentifier>
) : ConsensualTransactionMetaData {
}