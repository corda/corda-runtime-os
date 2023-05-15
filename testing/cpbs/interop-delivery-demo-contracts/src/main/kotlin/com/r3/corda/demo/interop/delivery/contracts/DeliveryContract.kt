package com.r3.corda.demo.interop.delivery.contracts

import com.r3.corda.demo.interop.delivery.states.DeliveryState
import net.corda.v5.base.exceptions.CordaRuntimeException
import net.corda.v5.ledger.utxo.Command
import net.corda.v5.ledger.utxo.Contract
import net.corda.v5.ledger.utxo.transaction.UtxoLedgerTransaction


class DeliveryContract : Contract {
    class Issue: Command
    class Transfer: Command

    override fun verify(transaction: UtxoLedgerTransaction) {

        val command = transaction.commands.singleOrNull() ?: throw CordaRuntimeException("Requires a single command.")

        "The output state should have two and only two participants." using {
            val output = transaction.outputContractStates.first() as DeliveryState
            output.participants.size == 2
        }

        when (command) {
            is Issue -> {
                "When command is Create there should be one and only one output state." using (transaction.outputContractStates.size == 1)
            }
            is Transfer -> {
                "When command is Transfer there should be one and only one output state." using (transaction.outputContractStates.size == 1)
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
