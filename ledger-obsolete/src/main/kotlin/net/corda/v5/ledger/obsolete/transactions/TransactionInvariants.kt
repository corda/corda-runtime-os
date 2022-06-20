@file:JvmName("TransactionInvariants")

package net.corda.v5.ledger.obsolete.transactions

import net.corda.v5.ledger.obsolete.notaries

/**
 * Check invariant properties of the class.
 */
fun checkBaseTransactionInvariants(transaction: BaseTransaction) {
    transaction.run {
        checkNotarySetIfInputsPresent()
        checkNoDuplicateInputs()
        checkForInputsAndReferencesOverlap()
    }
}

fun checkFullTransactionInvariants(transaction: FullTransaction) {
    checkBaseTransactionInvariants(transaction)
    transaction.checkInputsAndReferencesHaveSameNotary()
}

private fun BaseTransaction.checkNotarySetIfInputsPresent() {
    if (inputs.isNotEmpty() || references.isNotEmpty()) {
        check(notary != null) { "The notary must be specified explicitly for any transaction that has inputs" }
    }
}

private fun BaseTransaction.checkNoDuplicateInputs() {
    check(inputs.size == inputs.toSet().size) { "Duplicate input states detected" }
    check(references.size == references.toSet().size) { "Duplicate reference states detected" }
}

private fun BaseTransaction.checkForInputsAndReferencesOverlap() {
    val intersection = inputs intersect references
    require(intersection.isEmpty()) {
        "A StateRef cannot be both an input and a reference input in the same transaction. Offending StateRefs: $intersection"
    }
}

private fun FullTransaction.checkInputsAndReferencesHaveSameNotary() {
    if (inputs.isEmpty() && references.isEmpty()) return
    // Transaction can combine different identities of the same notary after key rotation.
    val notaries = (inputs + references).map { it.state.notary.name }.toHashSet()
    check(notaries.size == 1) { "All inputs and reference inputs must point to the same notary" }
    check(notaries.single() == notary?.name) {
        "The specified transaction notary must be the one specified by all inputs and input references"
    }
}

/** Make sure the assigned notary is part of the group parameter whitelist. */
fun FullTransaction.checkNotaryWhitelisted() {
    notary?.let { notaryParty ->
        // Group parameters will never be null if the transaction is resolved from a CoreTransaction rather than constructed directly.
        membershipParameters?.let { parameters ->
            val notaryWhitelist = parameters.notaries.map { it.party }
            // Transaction can combine different identities of the same notary after key rotation.
            // Each of these identities should be whitelisted.
            val notaries = setOf(notaryParty) + (inputs + references).map { it.state.notary }
            notaries.forEach {
                check(it in notaryWhitelist) {
                    "Notary [${it.description()}] specified by the transaction is not on the group parameter whitelist: " +
                            " [${notaryWhitelist.joinToString { party -> party.description() }}]"
                }
            }
        }
    }
}
