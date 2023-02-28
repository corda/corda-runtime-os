package net.cordapp.utxo.apples.contracts

import net.corda.v5.ledger.utxo.Command
import net.corda.v5.ledger.utxo.Contract
import net.corda.v5.ledger.utxo.transaction.UtxoLedgerTransaction
import net.corda.v5.ledger.utxo.transaction.getInputStates
import net.corda.v5.ledger.utxo.transaction.getOutputStates
import net.cordapp.utxo.apples.states.AppleStamp
import net.cordapp.utxo.apples.states.BasketOfApples

class BasketOfApplesContract : Contract{

    interface Commands : Command {
        class PackBasket : Commands
        class Redeem : Commands
    }

    override fun verify(transaction: UtxoLedgerTransaction) {
        // Extract the command from the transaction
        when (val command = transaction.commands.first()) {
            is Commands.PackBasket -> {
                // Retrieve the output state of the transaction
                val output = transaction.getOutputStates<BasketOfApples>().first()
                require(transaction.outputContractStates.size == 1) {
                    "This transaction should only output one BasketOfApples state"
                }
                require(output.description.isNotBlank()) {
                    "The output BasketOfApples state should have clear description of Apple product"
                }
                require(output.weight > 0) {
                    "The output BasketOfApples state should have non zero weight"
                }
            }
            is Commands.Redeem -> {
                // Retrieve the input and output state of the transaction
                val input = transaction.getInputStates<AppleStamp>().first()
                val output = transaction.getOutputStates<BasketOfApples>().first()
                require(transaction.inputContractStates.size == 2) {
                    "This transaction should consume two states"
                }
                require(input.issuer == output.farm) {
                    "The issuer of the Apple stamp should be the producing farm of this basket of apple"
                }

                require(output.weight > 0) {
                    "The basket of apple has to weight more than 0"
                }
            }
            else -> {
                throw IllegalArgumentException("Incorrect type of BasketOfApples commands: ${command::class.java.name}")
            }
        }
    }
}