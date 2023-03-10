package net.cordapp.testing.packagingverification.contract

import net.corda.v5.ledger.utxo.Contract
import net.corda.v5.ledger.utxo.transaction.UtxoLedgerTransaction
import org.slf4j.LoggerFactory

class SimpleContract : Contract {
    private companion object {
        val log = LoggerFactory.getLogger(this::class.java.enclosingClass)
    }

    override fun verify(transaction: UtxoLedgerTransaction) {
        log.info("Verify called on SimpleContract")
    }
}
