package net.corda.ledger.utxo.flow.impl.flows.transactiontransmission.v1

import net.corda.ledger.common.data.transaction.WireTransaction
import net.corda.ledger.utxo.flow.impl.flows.transactiontransmission.common.AbstractSendTransactionFlow
import net.corda.ledger.utxo.flow.impl.transaction.UtxoSignedTransactionInternal
import net.corda.sandbox.CordaSystemFlow
import net.corda.v5.application.messaging.FlowSession
import net.corda.v5.crypto.SecureHash
import net.corda.v5.ledger.utxo.transaction.UtxoSignedTransaction

@CordaSystemFlow
class SendLedgerTransactionFlowV1(
    private val signedTransaction: UtxoSignedTransaction,
    sessions: List<FlowSession>
) : AbstractSendTransactionFlow<WireTransaction>(
    (signedTransaction as UtxoSignedTransactionInternal).wireTransaction,
    sessions
) {

    override fun getTransactionDependencies(transaction: WireTransaction) =
        signedTransaction.inputStateRefs + signedTransaction.referenceStateRefs

    override fun getTransactionId(transaction: WireTransaction): SecureHash = transaction.id

    override fun getNotaryName(transaction: WireTransaction) = signedTransaction.notaryName
}
