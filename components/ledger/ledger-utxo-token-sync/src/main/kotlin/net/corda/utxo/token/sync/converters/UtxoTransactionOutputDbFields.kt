package net.corda.utxo.token.sync.converters

object UtxoTransactionOutputDbFields{
    const val TRANSACTION_ID = "transaction_id"
    const val GROUP_INDEX = "group_idx"
    const val LEAF_INDEX = "leaf_idx"
    const val TOKEN_TYPE = "token_type"
    const val TOKEN_ISSUER_HASH = "token_issuer_hash"
    const val TOKEN_NOTARY_X500_NAME = "token_notary_x500_name"
    const val TOKEN_SYMBOL = "token_symbol"
    const val TOKEN_TAG = "token_tag"
    const val TOKEN_OWNER_HASH = "token_owner_hash"
    const val TOKEN_AMOUNT = "token_amount"
    const val CREATED = "created"
    const val CONSUMED = "consumed"
}
