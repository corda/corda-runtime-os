package net.cordapp.utxo.apples.contracts

import net.corda.v5.ledger.utxo.Command
import net.corda.v5.ledger.utxo.Contract
import net.corda.v5.ledger.utxo.transaction.UtxoLedgerTransaction
import net.cordapp.utxo.apples.states.AppleStamp

class AppleStampContract : Contract {

    // Used to indicate the transaction's intent
    interface Commands : Command {
        // In our hello-world app, we will have two commands
        class Issue : Commands
    }

    override fun verify(transaction: UtxoLedgerTransaction) {
        // Extract the command from the transaction
        // Verify the transaction according to the intention of the transaction
        when (val command = transaction.commands.first()) {
            is Commands.Issue -> {
                val output = transaction.getOutputStates(AppleStamp::class.java).first()
                require(transaction.outputContractStates.size == 1) {
                    "This transaction should only have one AppleStamp state as output"
                }
                require(output.stampDesc.isNotBlank()) {
                    "The output AppleStamp state should have clear description of the type of redeemable goods"
                }
            }
            is BasketOfApplesContract.Commands.Redeem -> {
                // Transaction verification will happen in BasketOfApplesContract
            }
            else -> {
                // Unrecognised Command type
                throw IllegalArgumentException("Incorrect type of AppleStamp commands: ${command::class.java.name}")
            }
        }
    }
}
