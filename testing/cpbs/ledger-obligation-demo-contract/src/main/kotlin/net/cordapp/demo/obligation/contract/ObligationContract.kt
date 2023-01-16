package net.cordapp.demo.obligation.contract

import net.corda.v5.ledger.utxo.Command
import net.corda.v5.ledger.utxo.Contract
import net.corda.v5.ledger.utxo.transaction.UtxoLedgerTransaction
import net.corda.v5.ledger.utxo.transaction.getCommands
import net.corda.v5.ledger.utxo.transaction.getInputStates
import net.corda.v5.ledger.utxo.transaction.getOutputStates
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
        val command = transaction.getCommands<ObligationContractCommand>().singleOrNull()
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
     *
     * On [Create] of a new [ObligationState] we should at least check the following:
     * 1. We are not consuming any obligation state inputs, since all we want to do is create a new one.
     * 2. We are creating only one new obligation state output.
     * 3. The creditor and debtor of the new obligation are not the same participant.
     * 4. The amount of the new obligation is greater than zero.
     * 5. The debtor must sign the transaction.
     *
     * Note that since the obligation represents an IOU (I-owe-you) type obligation, it is the debtor who claims
     * responsibility for undertaking the obligation, therefore they are the only required signature. If we were to
     * include the creditor as a required signer, this would incur an empty transaction check during finality. This
     * sort of implicit signing holds no value since the creditor does not need to check or verify anything beyond the
     * contract constraints, therefore their signature is not required.
     */
    class Create : ObligationContractCommand {
        companion object {

            internal const val CONTRACT_RULE_INPUTS =
                "On state creating, zero input states must be consumed."

            internal const val CONTRACT_RULE_OUTPUTS =
                "On state creating, only one output state must be created."

            internal const val CONTRACT_RULE_PARTICIPANTS =
                "On state creating, the creditor and debtor must not be the same participant."

            internal const val CONTRACT_RULE_AMOUNT =
                "On state creating, the amount must be greater than zero."

            internal const val CONTRACT_RULE_SIGNATORIES =
                "On state creating, the debtor must sign the transaction."
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
     *
     * On [Update] of an existing [ObligationState] we should at least check the following:
     * 1. We are consuming only one existing obligation state input.
     * 2. We are creating only one new obligation state output.
     * 3. The creditor and debtor of the obligation must not change.
     * 4. The obligation must settle some, or all of the remaining amount.
     * 5. The obligation must not settle to produce a negative remaining amount.
     * 6. The debtor must sign the transaction.
     *
     * Note that since the obligation represents an IOU (I-owe-you) type obligation, it is the debtor who claims
     * responsibility for settling any amount of the obligation, therefore they are the only required signature.
     *
     * In reality this contract would likely also check for the existence of other state types in the transaction, like
     * a token or some other asset that is being transferred from the debtor to the creditor as settlement against the
     * obligation.
     *
     * Currently, there are no additional checks beyond contract verification required by the creditor, therefore as
     * with obligation creation, the creditor is not required to sign.
     */
    class Update : ObligationContractCommand {
        companion object {

            internal const val CONTRACT_RULE_INPUTS =
                "On state updating, only one input state must be consumed."

            internal const val CONTRACT_RULE_OUTPUTS =
                "On state updating, only one output state must be created."

            internal const val CONTRACT_RULE_CREDITOR =
                "On state updating, the creditor must not change."

            internal const val CONTRACT_RULE_DEBTOR =
                "On state updating, the debtor must not change."

            internal const val CONTRACT_RULE_AMOUNT_SETTLED =
                "On state updating, the output state amount must be less than the input state amount."

            internal const val CONTRACT_RULE_AMOUNT_REMAINING =
                "On state updating, the remaining amount must be greater than or equal to zero."

            internal const val CONTRACT_RULE_SIGNATORIES =
                "On state updating, the debtor must sign the transaction."
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
     *
     * On [Delete] of an existing [ObligationState] we should at least check the following:
     * 1. We are consuming only one existing obligation state input.
     * 2. We are not creating any new obligation state outputs.
     * 3. The amount of the obligation to be deleted (consumed) has reached zero.
     * 4. The creditor must sign the transaction.
     *
     * Note that since the obligation represents an IOU (I-owe-you) type obligation, it is the creditor who claims
     * responsibility for deleting a settled obligation, therefore they are the only required signature.
     *
     * Effectively, the creditor just closes out the contract, since the value of the obligation has reached zero.
     *
     * Currently, there are no additional checks beyond contract verification required by the debtor, therefore the
     * debtor is not required to sign.
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
                "On state deleting, the creditor must sign the transaction."
        }
    }

    /**
     * Verifies the current [ObligationContract] in the context of a [Create] command.
     *
     * @param transaction The transaction to verify.
     */
    private fun verifyCreate(transaction: UtxoLedgerTransaction) {
        val inputs = transaction.getInputStates<ObligationState>()
        val outputs = transaction.getOutputStates<ObligationState>()

        require(inputs.isEmpty()) { Create.CONTRACT_RULE_INPUTS }
        require(outputs.size == 1) { Create.CONTRACT_RULE_OUTPUTS }

        val output = outputs.single()

        require(output.creditor != output.debtor) { Create.CONTRACT_RULE_PARTICIPANTS }
        require(output.amount > BigDecimal.ZERO) { Create.CONTRACT_RULE_AMOUNT }
        require(output.debtor in transaction.signatories) { Create.CONTRACT_RULE_SIGNATORIES }
    }

    /**
     * Verifies the current [ObligationContract] in the context of an [Update] command.
     *
     * @param transaction The transaction to verify.
     */
    private fun verifyUpdate(transaction: UtxoLedgerTransaction) {
        val inputs = transaction.getInputStates<ObligationState>()
        val outputs = transaction.getOutputStates<ObligationState>()

        require(inputs.size == 1) { Update.CONTRACT_RULE_INPUTS }
        require(outputs.size == 1) { Update.CONTRACT_RULE_OUTPUTS }

        val input = inputs.single()
        val output = outputs.single()

        require(input.creditor == output.creditor) { Update.CONTRACT_RULE_CREDITOR }
        require(input.debtor == output.debtor) { Update.CONTRACT_RULE_DEBTOR }
        require(input.amount > output.amount) { Update.CONTRACT_RULE_AMOUNT_SETTLED }
        require(output.amount >= BigDecimal.ZERO) { Update.CONTRACT_RULE_AMOUNT_REMAINING }
        require(output.debtor in transaction.signatories) { Update.CONTRACT_RULE_SIGNATORIES }
    }

    /**
     * Verifies the current [ObligationContract] in the context of a [Delete] command.
     *
     * @param transaction The transaction to verify.
     */
    private fun verifyDelete(transaction: UtxoLedgerTransaction) {
        val inputs = transaction.getInputStates<ObligationState>()
        val outputs = transaction.getOutputStates<ObligationState>()

        require(inputs.size == 1) { Delete.CONTRACT_RULE_INPUTS }
        require(outputs.isEmpty()) { Delete.CONTRACT_RULE_OUTPUTS }

        val input = inputs.single()

        require(input.amount == BigDecimal.ZERO) { Delete.CONTRACT_RULE_AMOUNT }
        require(input.participants.all { it in transaction.signatories }) { Delete.CONTRACT_RULE_SIGNATORIES }
    }
}
