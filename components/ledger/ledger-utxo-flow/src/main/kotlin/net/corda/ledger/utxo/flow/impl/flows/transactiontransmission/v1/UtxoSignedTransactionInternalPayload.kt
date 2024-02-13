package net.corda.ledger.utxo.flow.impl.flows.transactiontransmission.v1

import net.corda.ledger.utxo.flow.impl.flows.transactiontransmission.common.AbstractUtxoTransactionPayload
import net.corda.ledger.utxo.flow.impl.transaction.UtxoSignedTransactionInternal

class UtxoSignedTransactionInternalPayload
    : AbstractUtxoTransactionPayload<UtxoSignedTransactionInternal>(mapOf())