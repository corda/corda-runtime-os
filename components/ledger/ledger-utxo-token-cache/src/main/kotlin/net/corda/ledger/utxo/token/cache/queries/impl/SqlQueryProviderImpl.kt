package net.corda.ledger.utxo.token.cache.queries.impl

import net.corda.ledger.utxo.token.cache.queries.SqlQueryProvider

class SqlQueryProviderImpl : SqlQueryProvider {

    companion object {
        val SQL_PARAMETER_TOKEN_TYPE = "tokenType"
        val SQL_PARAMETER_ISSUER_HASH = "issuerHash"
        val SQL_PARAMETER_SYMBOL = "symbol"
        val SQL_PARAMETER_OWNER_HASH = "ownerHash"
        val SQL_PARAMETER_TAG_FILTER = "tag"
    }

    override fun getPagedSelectQuery(limit: Int, includeTagFilter: Boolean, includeOwnerFilter: Boolean): String {
        val tagFilter = if(includeTagFilter){
            "AND   token_tag ~ :$SQL_PARAMETER_TAG_FILTER"
        }else{
            ""
        }
        val ownerFilter = if(includeTagFilter){
            "AND   token_owner_hash = :$SQL_PARAMETER_OWNER_HASH"
        }else{
            ""
        }

        return """
                SELECT
                    transaction_id,
                    leaf_idx,
                    token_tag,
                    token_owner_hash,
                    token_amount
                FROM {h-schema}utxo_transaction_output
                WHERE token_type = :$SQL_PARAMETER_TOKEN_TYPE
                AND   token_issuer_hash = :$SQL_PARAMETER_ISSUER_HASH
                AND   token_symbol = :$SQL_PARAMETER_SYMBOL
                $tagFilter
                $ownerFilter
                ORDER BY transaction_id
                LIMIT $limit
                """.trimIndent()
    }
}
