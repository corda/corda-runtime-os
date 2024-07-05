package net.corda.ledger.lib.impl.stub.transaction

import net.corda.ledger.common.data.transaction.WireTransaction
import net.corda.ledger.utxo.data.transaction.UtxoLedgerTransactionInternal
import net.corda.ledger.utxo.data.transaction.UtxoVisibleTransactionOutputDto
import net.corda.ledger.utxo.flow.impl.transaction.factory.UtxoLedgerTransactionFactory
import net.corda.v5.ledger.utxo.StateAndRef

class StubUtxoLedgerTransactionFactory : UtxoLedgerTransactionFactory {
    override fun create(wireTransaction: WireTransaction): UtxoLedgerTransactionInternal {
        TODO("Not yet implemented")
    }

    override fun create(
        wireTransaction: WireTransaction,
        inputStateAndRefs: List<UtxoVisibleTransactionOutputDto>,
        referenceStateAndRefs: List<UtxoVisibleTransactionOutputDto>
    ): UtxoLedgerTransactionInternal {
        TODO("Not yet implemented")
    }

    override fun createWithStateAndRefs(
        wireTransaction: WireTransaction,
        inputStateAndRefs: List<StateAndRef<*>>,
        referenceStateAndRefs: List<StateAndRef<*>>
    ): UtxoLedgerTransactionInternal {
        TODO("Not yet implemented")
    }
}