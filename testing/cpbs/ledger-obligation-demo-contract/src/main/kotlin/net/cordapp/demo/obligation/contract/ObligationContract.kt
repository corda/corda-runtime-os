package net.cordapp.demo.obligation.contract

import net.corda.v5.ledger.utxo.Command
import net.corda.v5.ledger.utxo.Contract
import net.corda.v5.ledger.utxo.transaction.UtxoLedgerTransaction
import java.math.BigDecimal

/**
 * Represents the contract that governs obligation state transitions on a UTXO ledger.
 */
class ObligationContract : Contract {

    /**
     * Verifies the specified transaction associated with the current contract.
     *
     * @param transaction The transaction to verify.
     */
    override fun verify(transaction: UtxoLedgerTransaction) {
        val command = transaction.getCommands(ObligationContractCommand::class.java).singleOrNull()
            ?: throw IllegalArgumentException("Expected a single command of type: ${ObligationContractCommand::class.java}.")

        /**
         * Verification logic for each command has been separated out into its own method/function as this leads to
         * increased readability and maintainability. This also better enables subclass verification for contract
         * hierarchies that derive from a contract superclass.
         */
        when (command) {
            is Create -> verifyCreate(transaction)
            is Update -> verifyUpdate(transaction)
            is Delete -> verifyDelete(transaction)
        }
    }

    /**
     * Defines a [Command] type to be used specifically within the current [ObligationContract].
     */
    private interface ObligationContractCommand : Command

    /**
     * Represents the create command, which is executed when obligations are created on a UTXO ledger.
     *
     * The [Create] command's companion object contains string constants which define the contract constraints as
     * human-readable text. Compared to using string literals directly within the contract verification logic, this
     * adds value in that the constant strings can be accessed from contract tests, leading to fewer repetitions.
     *
     * The constant strings are marked internal as they only need to be accessed within this [Contract] class, or from
     * associated tests within the same package.
     */
    class Create : ObligationContractCommand {
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
    }

    /**
     * Represents the update command, which is executed when obligations are updated on a UTXO ledger.
     *
     * The [Update] command's companion object contains string constants which define the contract constraints as
     * human-readable text. Compared to using string literals directly within the contract verification logic, this
     * adds value in that the constant strings can be accessed from contract tests, leading to fewer repetitions.
     *
     * The constant strings are marked internal as they only need to be accessed within this [Contract] class, or from
     * associated tests within the same package.
     */
    class Update : ObligationContractCommand {
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
    }

    /**
     * Represents the delete command, which is executed when obligations are deleted from a UTXO ledger.
     *
     * Due to the immutable nature of the UTXO ledger, contract states are never actually deleted; rather they are
     * marked as consumed and can no longer be used as inputs in subsequent transactions.
     *
     * The [Delete] command's companion object contains string constants which define the contract constraints as
     * human-readable text. Compared to using string literals directly within the contract verification logic, this
     * adds value in that the constant strings can be accessed from contract tests, leading to fewer repetitions.
     *
     * The constant strings are marked internal as they only need to be accessed within this [Contract] class, or from
     * associated tests within the same package.
     */
    class Delete : ObligationContractCommand {
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
    }

    /**
     * Verifies the current [ObligationContract] in the context of a [Create] command.
     *
     * @param transaction The transaction to verify.
     */
    private fun verifyCreate(transaction: UtxoLedgerTransaction) {
        // TODO : The generic variants of this don't work properly!
        val inputs = transaction.getInputStates(ObligationState::class.java)
        val outputs = transaction.getOutputStates(ObligationState::class.java)

        require(inputs.isEmpty()) { Create.CONTRACT_RULE_INPUTS }
        require(outputs.size == 1) { Create.CONTRACT_RULE_OUTPUTS }

        val output = outputs.single()

        require(output.issuer != output.holder) { Create.CONTRACT_RULE_PARTICIPANTS }
        require(output.amount > BigDecimal.ZERO) { Create.CONTRACT_RULE_AMOUNT }
        require(output.issuer in transaction.signatories) { Create.CONTRACT_RULE_SIGNATORIES }
    }

    /**
     * Verifies the current [ObligationContract] in the context of an [Update] command.
     *
     * @param transaction The transaction to verify.
     */
    private fun verifyUpdate(transaction: UtxoLedgerTransaction) {
        val inputs = transaction.getInputStates(ObligationState::class.java)
        val outputs = transaction.getOutputStates(ObligationState::class.java)

        require(inputs.size == 1) { Update.CONTRACT_RULE_INPUTS }
        require(outputs.size == 1) { Update.CONTRACT_RULE_OUTPUTS }

        val input = inputs.single()
        val output = outputs.single()

        require(input.issuer == output.issuer) { Update.CONTRACT_RULE_ISSUER }
        require(input.holder == output.holder) { Update.CONTRACT_RULE_HOLDER }
        require(input.amount > output.amount) { Update.CONTRACT_RULE_AMOUNT_CONSERVATION }
        require(output.amount >= BigDecimal.ZERO) { Update.CONTRACT_RULE_AMOUNT_OUTPUT }
        require(output.holder in transaction.signatories) { Update.CONTRACT_RULE_SIGNATORIES }
    }

    /**
     * Verifies the current [ObligationContract] in the context of a [Delete] command.
     *
     * @param transaction The transaction to verify.
     */
    private fun verifyDelete(transaction: UtxoLedgerTransaction) {
        val inputs = transaction.getInputStates(ObligationState::class.java)
        val outputs = transaction.getOutputStates(ObligationState::class.java)

        require(inputs.size == 1) { Delete.CONTRACT_RULE_INPUTS }
        require(outputs.isEmpty()) { Delete.CONTRACT_RULE_OUTPUTS }

        val input = inputs.single()

        require(input.amount == BigDecimal.ZERO) { Delete.CONTRACT_RULE_AMOUNT }
        require(input.participants.all { it in transaction.signatories }) { Delete.CONTRACT_RULE_SIGNATORIES }
    }
}
