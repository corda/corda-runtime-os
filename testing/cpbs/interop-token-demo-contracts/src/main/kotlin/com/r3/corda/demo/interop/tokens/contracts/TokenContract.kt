package com.r3.corda.demo.interop.tokens.contracts

import com.r3.corda.demo.interop.tokens.states.TokenState
import net.corda.v5.base.exceptions.CordaRuntimeException
import net.corda.v5.ledger.utxo.Command
import net.corda.v5.ledger.utxo.Contract
import net.corda.v5.ledger.utxo.transaction.UtxoLedgerTransaction


class TokenContract : Contract {
    class Issue: Command
    class Transfer: Command

    override fun verify(transaction: UtxoLedgerTransaction) {

        val command = transaction.commands.singleOrNull() ?: throw CordaRuntimeException("Requires a single command.")

        "There should be only one output state" using { transaction.outputContractStates.size == 1 }

        val outputState = transaction.outputContractStates.first() as TokenState

        when (command) {
            is Issue -> {
                "When command is Issue there should be exactly one participant." using (outputState.participants.size == 1)
            }
            is Transfer -> {
                "When command is Transfer there should be exactly two participants." using (outputState.participants.size == 2)
            }
            else -> {
                throw CordaRuntimeException("Command not allowed.")
            }
        }
    }

    private infix fun String.using(expr: Boolean) {
        if (!expr) throw CordaRuntimeException("Failed requirement: $this")
    }

    private infix fun String.using(expr: () -> Boolean) {
        if (!expr.invoke()) throw CordaRuntimeException("Failed requirement: $this")
    }
}
