package com.r3.corda.atomic.swap.contracts

import com.r3.corda.atomic.swap.states.Asset
import net.corda.v5.base.exceptions.CordaRuntimeException
import net.corda.v5.ledger.utxo.Command
import net.corda.v5.ledger.utxo.Contract
import net.corda.v5.ledger.utxo.transaction.UtxoLedgerTransaction


class AssetContract: Contract {

    internal companion object {
        const val REQUIRE_SINGLE_COMMAND = "Requires a single command."
        const val REQUIRES_ZERO_INPUTS = "The transaction requires zero inputs"
        const val REQUIRES_ONE_OUTPUT = "The transaction requires one output"
        const val REQUIRES_ONE_ASSET_OUTPUT = "The transaction requires one Asset output"
        const val REQUIRES_ONE_ASSET_INPUT = "The transaction requires one Asset input"
        const val REQUIRES_OWNER_SIGN = "Owner must sign the transaction"
        const val REQUIRES_DIFFERENT_OWNER = "Owner must change in this transaction"
    }

    interface AssetCommands : Command {
        class Create: AssetCommands
        class Transfer: AssetCommands
    }


    override fun verify(transaction: UtxoLedgerTransaction) {
        val command = transaction.getCommands(AssetCommands::class.java).singleOrNull()  ?: throw CordaRuntimeException(
            REQUIRE_SINGLE_COMMAND
        )
        // Switches case based on the command
        when(command) {
            // Rules applied only to transactions with the Create Command.
            is AssetCommands.Create -> {
                REQUIRES_ZERO_INPUTS using (transaction.inputContractStates.isEmpty())
                REQUIRES_ONE_OUTPUT using (transaction.outputContractStates.size == 1)
                REQUIRES_ONE_ASSET_OUTPUT using (transaction.getOutputStates(Asset::class.java).size == 1)

                val asset = transaction.getOutputStates(Asset::class.java)[0]
                REQUIRES_OWNER_SIGN using (transaction.signatories.contains(asset.owner))
            }
            // Rules applied only to transactions with the Update Command.
            is AssetCommands.Transfer -> {
                REQUIRES_ONE_ASSET_OUTPUT using (transaction.getOutputStates(Asset::class.java).size == 1)
                REQUIRES_ONE_ASSET_INPUT using (transaction.getInputStates(Asset::class.java).size == 1)

                val input = transaction.getInputStates(Asset::class.java)[0]
                REQUIRES_OWNER_SIGN using (transaction.signatories.contains(input.owner))

                val output = transaction.getOutputStates(Asset::class.java)[0]
                REQUIRES_DIFFERENT_OWNER using (input.owner != output.owner)
            }
            else -> {
                throw CordaRuntimeException("Invalid Command")
            }
        }
    }

    // Helper function to allow writing constraints in the Corda 4 '"text" using (boolean)' style
    private infix fun String.using(expr: Boolean) {
        if (!expr) throw CordaRuntimeException("Failed requirement: $this")
    }

    // Helper function to allow writing constraints in '"text" using {lambda}' style where the last expression
    // in the lambda is a boolean.
    private infix fun String.using(expr: () -> Boolean) {
        if (!expr.invoke()) throw CordaRuntimeException("Failed requirement: $this")
    }
}