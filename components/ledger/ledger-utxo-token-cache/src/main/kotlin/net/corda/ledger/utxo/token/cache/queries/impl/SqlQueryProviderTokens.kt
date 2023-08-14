package net.corda.ledger.utxo.token.cache.queries.impl

import net.corda.ledger.utxo.token.cache.queries.SqlQueryProvider
import org.osgi.service.component.annotations.Component

@Component(
    service = [ SqlQueryProvider::class]
)
class SqlQueryProviderTokens : SqlQueryProvider {

    companion object {
        const val SQL_PARAMETER_TOKEN_TYPE = "tokenType"
        const val SQL_PARAMETER_ISSUER_HASH = "issuerHash"
        const val SQL_PARAMETER_SYMBOL = "symbol"
        const val SQL_PARAMETER_OWNER_HASH = "ownerHash"
        const val SQL_PARAMETER_TAG_FILTER = "tag"
    }

    override fun getBalanceQuery(includeTagFilter: Boolean, includeOwnerFilter: Boolean): String {
        val tagFilter = if(includeTagFilter){
            "AND regexp_like(token_tag, :$SQL_PARAMETER_TAG_FILTER)"
        }else{
            ""
        }
        val ownerFilter = if(includeOwnerFilter){
            "AND   token_owner_hash = :$SQL_PARAMETER_OWNER_HASH"
        }else{
            ""
        }

        return """
            SELECT 
                COALESCE(SUM(token_amount), 0)
            FROM {h-schema}utxo_transaction_output as t_output
            INNER JOIN {h-schema}utxo_visible_transaction_state as t_state 
            ON t_output.transaction_id = t_state.transaction_id 
            AND t_output.group_idx = t_state.group_idx 
            AND t_output.leaf_idx = t_state.leaf_idx 
            WHERE t_state.consumed is null
            AND token_type = :$SQL_PARAMETER_TOKEN_TYPE
            AND token_issuer_hash = :$SQL_PARAMETER_ISSUER_HASH
            AND token_symbol = :$SQL_PARAMETER_SYMBOL
            $tagFilter
            $ownerFilter
        """.trimIndent()
    }

    override fun getPagedSelectQuery(limit: Int, includeTagFilter: Boolean, includeOwnerFilter: Boolean): String {
        val tagFilter = if(includeTagFilter){
            "AND regexp_like(token_tag, :$SQL_PARAMETER_TAG_FILTER)"
        }else{
            ""
        }
        val ownerFilter = if(includeOwnerFilter){
            "AND   token_owner_hash = :$SQL_PARAMETER_OWNER_HASH"
        }else{
            ""
        }

        // The query orders by transaction_id to create some randomness in the tokens that are selected
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
