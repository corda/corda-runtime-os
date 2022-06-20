package net.corda.v5.ledger.obsolete.transactions

import net.corda.v5.crypto.SecureHash
import net.corda.v5.ledger.obsolete.contracts.Contract
import net.corda.v5.ledger.obsolete.contracts.TransactionVerificationException

/**
 * A set of functions in core:test that allows testing of core internal classes in the core-tests project.
 */

fun WireTransaction.accessGroupHashes() = this.groupHashes
fun WireTransaction.accessGroupMerkleRoots() = this.groupsMerkleRoots
fun WireTransaction.accessAvailableComponentHashes() = this.availableComponentHashes

fun createContractCreationError(txId: SecureHash, contractClass: String, cause: Throwable) = TransactionVerificationException.ContractCreationError(txId, contractClass, cause)
fun createContractRejection(txId: SecureHash, contract: Contract, cause: Throwable): TransactionVerificationException.ContractRejection {
    val contractRejection = TransactionVerificationException.ContractRejection(txId, contract, cause)
    return contractRejection
}
