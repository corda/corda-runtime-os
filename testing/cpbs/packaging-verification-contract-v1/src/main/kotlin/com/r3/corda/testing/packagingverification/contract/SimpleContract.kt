package com.r3.corda.testing.packagingverification.contract

import net.corda.v5.ledger.utxo.Contract
import net.corda.v5.ledger.utxo.transaction.UtxoLedgerTransaction
import org.slf4j.LoggerFactory

class SimpleContract : Contract {
    private companion object {
        val log = LoggerFactory.getLogger(this::class.java.enclosingClass)
    }

    override fun verify(transaction: UtxoLedgerTransaction) {
        log.info("Verify called on SimpleContract")

        require(transaction.commands.size == 1) { "Too many commands on transaction" }

        when (transaction.commands.first()) {
            is MintCommand -> {
                require(transaction.inputContractStates.size == 0) { "Minting states: should not have inputs" }
                // In a real contract we would check states have same issuer
            }
            is TransferCommand -> {
                require( transaction.inputContractStates.size > 0) { "Transfer states: no input states" }
                require( transaction.outputContractStates.size > 0) { "Transfer states: no output states" }
                // In a real contract we would check sum of all inputs == sum of all outputs
            }
        }
    }
}
