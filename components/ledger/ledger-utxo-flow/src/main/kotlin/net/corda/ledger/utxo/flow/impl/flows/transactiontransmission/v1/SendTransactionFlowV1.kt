package net.corda.ledger.utxo.flow.impl.flows.transactiontransmission.v1

import net.corda.ledger.utxo.flow.impl.flows.transactiontransmission.common.AbstractSendTransactionFlow
import net.corda.ledger.utxo.flow.impl.transaction.UtxoSignedTransactionInternal
import net.corda.sandbox.CordaSystemFlow
import net.corda.v5.application.messaging.FlowSession
import net.corda.v5.ledger.utxo.transaction.UtxoSignedTransaction

@CordaSystemFlow
class SendTransactionFlowV1(
    transaction: UtxoSignedTransaction,
    sessions: List<FlowSession>
) : AbstractSendTransactionFlow<UtxoSignedTransactionInternal>(
    transaction as UtxoSignedTransactionInternal, sessions
) {
    override fun getTransactionDependencies(transaction: UtxoSignedTransactionInternal) =
        transaction.inputStateRefs + transaction.referenceStateRefs

    override fun getTransactionId(transaction: UtxoSignedTransactionInternal) =
        transaction.id

    override fun getNotaryName(transaction: UtxoSignedTransactionInternal) =
        transaction.notaryName
}
