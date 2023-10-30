package com.r3.corda.atomic.swap.contracts

import com.r3.corda.atomic.swap.states.Asset
import net.corda.v5.base.exceptions.CordaRuntimeException
import net.corda.v5.ledger.utxo.Command
import net.corda.v5.ledger.utxo.Contract
import net.corda.v5.ledger.utxo.transaction.UtxoLedgerTransaction


class AssetContract : Contract {

    interface AssetCommands : Command {
        class Create : AssetCommands
        class Transfer : AssetCommands
    }

    override fun verify(transaction: UtxoLedgerTransaction) {
        val command = transaction.getCommands(AssetCommands::class.java).singleOrNull() ?: throw CordaRuntimeException(
            "Requires a single command."
        )
        when (command) {
            is AssetCommands.Create -> {
                "The transaction requires zero inputs." using (transaction.inputContractStates.isEmpty())
                "The transaction requires one output." using (transaction.outputContractStates.size == 1)
                "The transaction requires one Asset output." using (transaction.getOutputStates(Asset::class.java).size == 1)

                val asset = transaction.getOutputStates(Asset::class.java).first()
                "Owner must sign the transaction." using (transaction.signatories.contains(asset.owner))
            }

            is AssetCommands.Transfer -> {
                "The transaction requires one Asset output." using (transaction.getOutputStates(Asset::class.java).size == 1)
                "The transaction requires one Asset input." using (transaction.getInputStates(Asset::class.java).size == 1)

                val output = transaction.getOutputStates(Asset::class.java).first()

                "Both old and new owner must sign the transaction." using (transaction.signatories.containsAll(output.participants))
            }

            else -> {
                throw CordaRuntimeException("Invalid Command")
            }
        }
    }

    private infix fun String.using(expr: Boolean) {
        if (!expr) throw CordaRuntimeException("Failed requirement: $this")
    }
}