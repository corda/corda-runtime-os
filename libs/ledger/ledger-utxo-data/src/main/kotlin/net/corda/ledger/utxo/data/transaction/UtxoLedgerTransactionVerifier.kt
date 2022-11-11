package net.corda.ledger.utxo.data.transaction

import net.corda.v5.ledger.utxo.Command
import net.corda.v5.ledger.utxo.Contract
import net.corda.v5.ledger.utxo.ContractVerificationException
import net.corda.v5.ledger.utxo.ContractVerificationFailureReason
import net.corda.v5.ledger.utxo.transaction.UtxoLedgerTransaction
import kotlin.reflect.full.createInstance

/**
 * Represents a UTXO ledger transaction verifier.
 *
 * @property transaction The [UtxoLedgerTransaction] being verified.
 * @property failureReasons The reasons given for each command failure.
 */
class UtxoLedgerTransactionVerifier(private val transaction: UtxoLedgerTransaction) {

    private val failureReasons = mutableListOf<ContractVerificationFailureReason>()

    /**
     * Verifies the specified [UtxoLedgerTransaction].
     *
     * @throws ContractVerificationException if any of the commands fail to be verified successfully.
     *
     * The verification process is as follows:
     *
     * 1. Get all contract classes from the transaction input and output state and refs.
     *
     * For each command...
     *
     * 2. Recursively walk up the class tree until a [Contract] class is found.
     * If no contract class is found, throw an exception and add it to the list of failure reasons.
     *
     * 3. Check that the command's contract class is assignable from one of the transaction contract classes.
     * If the command's contract class is not assignable, throw an exception and add it to the list of failure reasons.
     *
     * 4. Instantiate the command's contract class using the primary parameterless constructor.
     * If no parameterless constructor is found, an exception is thrown and added to the list of failure reasons.
     *
     * 5. Verify the contract using the specified [UtxoLedgerTransaction].
     * If contract verification fails, an exception is thrown and added to the list of failure reasons.
     *
     * 6. If the list of failure reasons is not empty, throw a [ContractVerificationException] containing all failure reasons.
     */
    fun verify() {
        val allTransactionStateAndRefs = transaction.inputStateAndRefs + transaction.outputStateAndRefs
        val contractClassesForStateAndRefs = allTransactionStateAndRefs.map { it.state.contractType }.distinct()

        transaction.commands.forEach {
            try {

                val contractClassForCommand = getEnclosingContractClass(it.javaClass)

                contractClassForCommand.checkIsAssignableFromAnyContractClass(contractClassesForStateAndRefs)

                val contract = contractClassForCommand.kotlin.createInstance()

                contract.verify(transaction)

            } catch (ex: Exception) {
                failureReasons.add(ContractVerificationFailureReasonImpl(it.javaClass, ex))
            }
        }

        if (failureReasons.isNotEmpty()) {
            throw ContractVerificationException(transaction.id, failureReasons)
        }
    }

    /**
     * Gets the enclosing [Contract] class by recursively walking up the class tree where the [Command] is implemented.
     *
     * @param commandClass The command class from which to obtain a [Contract] class.
     * @param currentClass The current class in the class tree, starting with the command class.
     * @throws IllegalStateException if the current class is null.
     *
     * 1. If the current class is null, then we've exhausted the class tree and failed to find a [Contract] class.
     * 2. If the current class is assignable to Contract, then we've found the enclosing contract class.
     * 3. Recursively walk up the class tree by passing in the current class's enclosing class.
     */
    @Suppress("UNCHECKED_CAST")
    private tailrec fun getEnclosingContractClass(
        commandClass: Class<out Command>,
        currentClass: Class<*>? = commandClass
    ): Class<out Contract> {

        checkNotNull(currentClass) {
            "Failed to obtain a contract class for the specified command: ${commandClass.canonicalName}."
        }

        return if (Contract::class.java.isAssignableFrom(currentClass)) currentClass as Class<out Contract>
        else getEnclosingContractClass(commandClass, currentClass.enclosingClass)
    }

    /**
     * Checks that the current [Contract] class is assignable from any of the specified [Contract] classes.
     *
     * @receiver The [Contract] class to check whether it is assignable from any of the specified [Contract] classes.
     * @param contractClasses The contract classes to check whether they are assignable from the current [Contract] class.
     * @throws IllegalStateException if the current [Contract] class is not assignable from any of the specified [Contract] classes.
     */
    private fun Class<out Contract>.checkIsAssignableFromAnyContractClass(contractClasses: Iterable<Class<out Contract>>) {
        check(contractClasses.any { it.isAssignableFrom(this) }) {
            "The specified command contract class is not assignable from any of the transaction state contracts: ${this.canonicalName}."
        }
    }
}
