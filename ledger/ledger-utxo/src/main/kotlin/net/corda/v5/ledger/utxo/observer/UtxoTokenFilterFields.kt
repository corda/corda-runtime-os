package net.corda.v5.ledger.utxo.observer

import net.corda.v5.crypto.SecureHash

/**
 * The [UtxoTokenFilterFields] provide optional fields to allow CorDapps to filter for subsets of tokens within a token
 * pool.
 *
 * @property tag Optional user defined string that can be used for regular expression filters.
 * @ownerHash Optional token owner hash.
 */
data class UtxoTokenFilterFields(
    val tag: String?,
    val ownerHash: SecureHash?
){
    constructor():this(null,null)
}