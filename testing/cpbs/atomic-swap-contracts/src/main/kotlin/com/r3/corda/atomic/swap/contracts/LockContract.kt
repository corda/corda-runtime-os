package com.r3.corda.atomic.swap.contracts

import com.r3.corda.atomic.swap.states.LockState
import com.r3.corda.ledger.utxo.ownable.OwnableState
import net.corda.v5.base.exceptions.CordaRuntimeException
import net.corda.v5.ledger.utxo.Command
import net.corda.v5.ledger.utxo.Contract
import net.corda.v5.ledger.utxo.transaction.UtxoLedgerTransaction


class LockContract : Contract {

    interface LockCommands : Command {
        class Lock : LockCommands
        class Unlock(val bool: Boolean) : LockCommands
    }

    override fun verify(transaction: UtxoLedgerTransaction) {

        val command = transaction.getCommands(LockCommands::class.java).singleOrNull()
            ?: throw CordaRuntimeException("Requires a single command.")

        val outputState = transaction.getOutputStates(OwnableState::class.java).first()

        when (command) {
            is LockCommands.Lock -> {
                "When command is Lock there should be exactly two participants." using (outputState.participants.size == 2)
                "There should be no lock input states" using (transaction.getInputStates(LockState::class.java)
                    .isEmpty())
                "There should be only one lock output state" using (transaction.getOutputStates(LockState::class.java).size == 1)
            }

            is LockCommands.Unlock -> {
                val input = transaction.getInputStates(LockState::class.java).singleOrNull()
                    ?: throw CordaRuntimeException("Can't find lock state for verification")
                "Unlock takes one command input of type Boolean and the value should be true." using
                        (command.bool == input.bool)
                "There should be one input state as the lock state needs to be consumed" using
                        (transaction.getInputStates(LockState::class.java).size == 1)
                "There should be one output state as an unlocked asset state needs to be created" using
                        (transaction.outputContractStates.size == 1)
                "The new owner should not be the old owner" using (outputState.owner != input.creator)
                "The new owner should be the same as the receiver of the lock state" using
                        (outputState.owner == input.receiver)
                "The signer of the unlock should be the new owner of the Asset state." using
                        (outputState.owner == transaction.signatories.first())
            }

            else -> {
                throw CordaRuntimeException("Command not allowed.")
            }
        }
    }

    private infix fun String.using(expr: Boolean) {
        if (!expr) throw CordaRuntimeException("Failed requirement: $this")
    }
}
