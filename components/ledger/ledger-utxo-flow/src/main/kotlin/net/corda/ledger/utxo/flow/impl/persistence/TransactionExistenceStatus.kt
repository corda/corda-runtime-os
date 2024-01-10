package net.corda.ledger.utxo.flow.impl.persistence

/**
 * [TransactionExistenceStatus] represents the current existence of a transaction and what its status is.
 */
enum class TransactionExistenceStatus {
    DOES_NOT_EXIST, UNVERIFIED, VERIFIED
}