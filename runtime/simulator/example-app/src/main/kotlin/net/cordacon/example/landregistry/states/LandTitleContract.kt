package net.cordacon.example.landregistry.states

import net.corda.v5.base.exceptions.CordaRuntimeException
import net.corda.v5.ledger.utxo.Command
import net.corda.v5.ledger.utxo.Contract
import net.corda.v5.ledger.utxo.transaction.UtxoLedgerTransaction

class LandTitleContract: Contract {

    override fun verify(transaction: UtxoLedgerTransaction) {
        val command = transaction.getCommands(LandTitleCommand::class.java).singleOrNull()
            ?: throw CordaRuntimeException("Expected a single command of type: ${LandTitleCommand::class.java}.")
        command.verify(transaction)
    }

    private interface LandTitleCommand : Command {
        fun verify(transaction: UtxoLedgerTransaction)
    }

    object Issue : LandTitleCommand {
        override fun verify(transaction: UtxoLedgerTransaction) {
            val inputs = transaction.getInputStates(LandTitleState::class.java)
            val outputs = transaction.getOutputStates(LandTitleState::class.java)

            require(inputs.isEmpty()) { "Inputs should not be consumed while issuance." }
            require(outputs.size == 1) { "Only one output state must be created." }

            val output = outputs.single()
            require(output.issuer in transaction.signatories) { "Issuer must sign the transaction" }
        }

    }
    object Transfer : LandTitleCommand {
        override fun verify(transaction: UtxoLedgerTransaction) {
            val inputs = transaction.getInputStates(LandTitleState::class.java)
            val outputs = transaction.getOutputStates(LandTitleState::class.java)

            require(inputs.size == 1) { "Only one input state must be consumed." }
            require(outputs.size == 1) { "Only one output state must be created." }

            val input = inputs.single()
            val output = outputs.single()

            require(input.issuer == output.issuer) { "The issuer should not change while land title transfer" }
            require(input.owner != output.owner) { "The owner should change" }
            require(output.issuer in transaction.signatories) { "The issuer must sign the transaction" }
            require(input.owner in transaction.signatories) { "The current owner must sign the transaction" }
        }

    }

}