package com.r3.corda.atomic.swap.contracts

import com.r3.corda.atomic.swap.states.LockState
import net.corda.v5.base.exceptions.CordaRuntimeException
import net.corda.v5.ledger.utxo.Command
import net.corda.v5.ledger.utxo.Contract
import net.corda.v5.ledger.utxo.transaction.UtxoLedgerTransaction


class LockContract : Contract {
    class Lock : Command
    class Unlock(val bool: Boolean) : Command

    override fun verify(transaction: UtxoLedgerTransaction) {

        val command = transaction.commands.singleOrNull() ?: throw CordaRuntimeException("Requires a single command.")

        "There should be only one output state" using { transaction.outputContractStates.size == 1 }

//        val inputState = transaction.inputContractStates.first() as LockState
        val outputState = transaction.outputContractStates.first() as LockState

        //todo change the rules of the commands
        when (command) {
            is Lock -> {
                "When command is Lock there should be exactly two participants." using (outputState.participants.size == 2)
                "There should be no lock input states" using (transaction.inputStateAndRefs.isEmpty())
                "There should be only one lock output states" using (transaction.outputStateAndRefs.size == 1 &&
                        transaction.outputStateAndRefs.javaClass.isInstance(
                            LockState::class.java
                        ))
            }

            is Unlock -> {
                "When command is Unlock there should be exactly two participants." using (outputState.participants.size == 2)
                "Unlock takes one command input of type Boolean and the value should be true." using
                        (command.bool.javaClass.isInstance(
                            Boolean
                        ) && command.bool)
                "There should be one input state as the lock state needs to be consumed" using
                        (transaction.inputStateRefs.javaClass.isInstance(
                            LockState::class.java
                        ))
                "There should be one output state as an unlocked asset state needs to be created" using
                        (transaction.outputStateAndRefs.size == 1)
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
