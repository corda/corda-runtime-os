package net.corda.ledger.lib.impl.stub.verification

import net.corda.ledger.utxo.flow.impl.transaction.verifier.UtxoLedgerTransactionVerificationService
import net.corda.v5.ledger.utxo.transaction.UtxoLedgerTransaction

class StubUtxoLedgerTransactionVerificationService : UtxoLedgerTransactionVerificationService {
    override fun verify(transaction: UtxoLedgerTransaction) {
        TODO("Not yet implemented")
    }
}