package net.corda.uniqueness.datamodel

/**
 * Internal representation of the transaction details that must be persisted by the uniqueness
 * checker, used by the uniqueness checker and backing store only. This representation is agnostic
 * to both the message bus API and any DB schema that may be used to persist data by the backing
 * store.
 *
 * @param txAlgo The algorithm of the transaction ID
 * @param txId The ID of the transaction
 * @param result Internal result storing the result of the request
 */
data class UniquenessCheckInternalTransactionDetails(
    var txAlgo: String,
    var txId: ByteArray,
    var result: UniquenessCheckInternalResult
)
