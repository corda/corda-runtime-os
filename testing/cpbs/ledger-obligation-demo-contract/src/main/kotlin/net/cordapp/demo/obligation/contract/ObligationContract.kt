package net.cordapp.demo.obligation.contract

import net.corda.v5.ledger.utxo.Command
import net.corda.v5.ledger.utxo.Contract
import net.corda.v5.ledger.utxo.transaction.UtxoLedgerTransaction
import java.math.BigDecimal

class ObligationContract : Contract {

    override fun verify(transaction: UtxoLedgerTransaction) {
        val command = transaction.getCommands(TestUtxoContractCommand::class.java).singleOrNull()
            ?: throw IllegalArgumentException("Expected a single command of type: ${TestUtxoContractCommand::class.java}.")

        command.verify(transaction)
    }

    private interface TestUtxoContractCommand : Command {
        fun verify(transaction: UtxoLedgerTransaction)
    }

    class Create : TestUtxoContractCommand {

        companion object {

            internal const val CONTRACT_RULE_INPUTS =
                "On state creating, zero input states must be consumed."

            internal const val CONTRACT_RULE_OUTPUTS =
                "On state creating, only one output state must be created."

            internal const val CONTRACT_RULE_PARTICIPANTS =
                "On state creating, the issuer and holder must not be the same participant."

            internal const val CONTRACT_RULE_AMOUNT =
                "On state creating, the amount must be greater than zero."

            internal const val CONTRACT_RULE_SIGNATORIES =
                "On state creating, the issuer must sign the transaction."

        }

        override fun verify(transaction: UtxoLedgerTransaction) {

            // TODO : The generic variants of this don't work properly!
            val inputs = transaction.getInputStates(ObligationState::class.java)
            val outputs = transaction.getOutputStates(ObligationState::class.java)

            require(inputs.isEmpty()) { CONTRACT_RULE_INPUTS }
            require(outputs.size == 1) { CONTRACT_RULE_OUTPUTS }

            val output = outputs.single()

            require(output.issuer != output.holder) { CONTRACT_RULE_PARTICIPANTS }
            require(output.amount > BigDecimal.ZERO) { CONTRACT_RULE_AMOUNT }
            require(output.issuer in transaction.signatories) { CONTRACT_RULE_SIGNATORIES }
        }
    }

    class Update : TestUtxoContractCommand {

        companion object {

            internal const val CONTRACT_RULE_INPUTS =
                "On state updating, only one input state must be consumed."

            internal const val CONTRACT_RULE_OUTPUTS =
                "On state updating, only one output state must be created."

            internal const val CONTRACT_RULE_ISSUER =
                "On state updating, the issuer must not change."

            internal const val CONTRACT_RULE_HOLDER =
                "On state updating, the holder must not change."

            internal const val CONTRACT_RULE_AMOUNT_CONSERVATION =
                "On state updating, the output state amount must be less than the input state amount."

            internal const val CONTRACT_RULE_AMOUNT_OUTPUT =
                "On state updating, the remaining amount must be more than or equal zero."

            internal const val CONTRACT_RULE_SIGNATORIES =
                "On state updating, the holder must sign the transaction."

        }

        override fun verify(transaction: UtxoLedgerTransaction) {
            val inputs = transaction.getInputStates(ObligationState::class.java)
            val outputs = transaction.getOutputStates(ObligationState::class.java)

            require(inputs.size == 1) { CONTRACT_RULE_INPUTS }
            require(outputs.size == 1) { CONTRACT_RULE_OUTPUTS }

            val input = inputs.single()
            val output = outputs.single()

            require(input.issuer == output.issuer) { CONTRACT_RULE_ISSUER }
            require(input.holder == output.holder) { CONTRACT_RULE_HOLDER }
            require(input.amount > output.amount) { CONTRACT_RULE_AMOUNT_CONSERVATION }
            require(output.amount >= BigDecimal.ZERO) { CONTRACT_RULE_AMOUNT_OUTPUT }
            require(output.holder in transaction.signatories) { CONTRACT_RULE_SIGNATORIES }
        }
    }

    class Delete : TestUtxoContractCommand {

        companion object {

            internal const val CONTRACT_RULE_INPUTS =
                "On state deleting, only one input state must be consumed."

            internal const val CONTRACT_RULE_OUTPUTS =
                "On state deleting, zero output states must be created."

            internal const val CONTRACT_RULE_AMOUNT =
                "On state deleting, the amount must be zero."

            internal const val CONTRACT_RULE_SIGNATORIES =
                "On state deleting, the issuer and the holder must sign the transaction."

        }

        override fun verify(transaction: UtxoLedgerTransaction) {
            val inputs = transaction.getInputStates(ObligationState::class.java)
            val outputs = transaction.getOutputStates(ObligationState::class.java)

            require(inputs.size == 1) { CONTRACT_RULE_INPUTS }
            require(outputs.isEmpty()) { CONTRACT_RULE_OUTPUTS }

            val input = inputs.single()

            require(input.amount == BigDecimal.ZERO) { CONTRACT_RULE_AMOUNT }
            require(input.participants.all { it in transaction.signatories }) { CONTRACT_RULE_SIGNATORIES }
        }
    }
}
