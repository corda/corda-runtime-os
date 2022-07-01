package net.corda.uniqueness.datamodel

/**
 * Internal representation of the state details that must be persisted by the uniqueness checker,
 * used by the uniqueness checker and backing store only. This representation is agnostic to both
 * the message bus API and any DB schema that may be used to persist data by the backing store.
 *
 * @param issueTxIdAlgo The algorithm of the transaction ID that issued this state
 * @param issueTxId The ID of the transaction that issued this state
 * @param stateIndex The state's index in the issuing transaction
 * @param consumingTxIdAlgo The algorithm of the transaction ID that consumed this state. Blank
 *                          if the state is unspent.
 * @param consumingTxId The ID of the transaction that consumed this state. Blank if the state is
 *                      unspent.
 */
data class UniquenessCheckInternalStateDetails(
    var issueTxIdAlgo: String,
    var issueTxId: ByteArray,
    var stateIndex: Long,
    var consumingTxIdAlgo: String?,
    var consumingTxId: ByteArray?
)
