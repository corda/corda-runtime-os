package net.corda.ledger.utxo.flow.impl.transaction.verifier

import net.corda.ledger.utxo.flow.impl.transaction.ContractVerificationFailureImpl
import net.corda.v5.ledger.common.Party
import net.corda.v5.ledger.utxo.ContractVerificationException
import net.corda.v5.ledger.utxo.ContractVerificationFailure
import net.corda.v5.ledger.utxo.transaction.UtxoLedgerTransaction

class UtxoLedgerTransactionVerifier(private val transaction: UtxoLedgerTransaction): UtxoTransactionVerifier()  {
    private val failureReasons = mutableListOf<ContractVerificationFailure>()
    override val subjectClass: String = UtxoLedgerTransaction::class.simpleName!!

    fun verifyPlatformChecks(notary: Party) {
        /**
         * These checks are shared with [UtxoTransactionBuilderVerifier] verification.
         * They do not require backchain resolution.
         */
        verifySignatories(transaction.signatories)
        verifyInputsAndOutputs(transaction.inputStateRefs, transaction.outputContractStates)
        verifyCommands(transaction.commands)
        verifyNotaryIsWhitelisted()

        /**
         * These checks require backchain resolution.
         */
        verifyInputNotaries(notary)
        verifyInputsAreOlderThanOutputs()
    }

    fun verifyContracts() {
        failureReasons.addAll(verifyEncumberedInput(transaction.inputStateAndRefs))

        val allTransactionStateAndRefs = transaction.inputStateAndRefs + transaction.outputStateAndRefs
        val contractClassMap = allTransactionStateAndRefs.groupBy { it.state.contractType }

        contractClassMap.forEach { (contractClass, contractStates) ->
            try {
                val contract = contractClass.getConstructor().newInstance()
                contract.verify(transaction)
            } catch (ex: Exception) {
                failureReasons.add(
                    ContractVerificationFailureImpl(
                        contractClassName = contractClass.canonicalName,
                        contractStateClassNames = contractStates.map { it.state.contractState.javaClass.canonicalName },
                        exceptionClassName = ex.javaClass.canonicalName,
                        exceptionMessage = ex.message ?: "The thrown exception did not provide a failure message."
                    )
                )
            }
        }

        if (failureReasons.isNotEmpty()) {
            throw ContractVerificationException(transaction.id, failureReasons)
        }
    }


    private fun verifyInputNotaries(notary: Party) {
        val allInputs = transaction.inputTransactionStates + transaction.referenceTransactionStates
        if(allInputs.isEmpty())
            return
        check(allInputs.map { it.notary }.distinct().size == 1) {
            "Input and Reference states' notaries need to be the same. ${allInputs.map { it.notary }.distinct().size}"
        }
        check(allInputs.first().notary == notary) {
            "Input and Reference states' notaries need to be the same as the $subjectClass's notary."
        }
        // TODO CORE-8958 check rotated notaries
    }

    private fun verifyInputsAreOlderThanOutputs(){
        // TODO CORE-8957 (needs to access the previous transactions from the backchain somehow)
    }

}