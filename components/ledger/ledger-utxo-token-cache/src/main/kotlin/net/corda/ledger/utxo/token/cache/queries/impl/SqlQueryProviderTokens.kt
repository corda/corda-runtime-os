package net.corda.ledger.utxo.token.cache.queries.impl

import net.corda.ledger.utxo.token.cache.queries.SqlQueryProvider
import org.osgi.service.component.annotations.Activate
import org.osgi.service.component.annotations.Component
import org.osgi.service.component.annotations.ServiceScope

@Component(
    service = [ SqlQueryProvider::class],
    scope = ServiceScope.PROTOTYPE
)
class SqlQueryProviderTokens @Activate constructor() : SqlQueryProvider {

    companion object {
        val SQL_PARAMETER_TOKEN_TYPE = "tokenType"
        val SQL_PARAMETER_ISSUER_HASH = "issuerHash"
        val SQL_PARAMETER_SYMBOL = "symbol"
        val SQL_PARAMETER_OWNER_HASH = "ownerHash"
        val SQL_PARAMETER_TAG_FILTER = "tag"
    }

    override fun getBalanceQuery(limit: Int, includeTagFilter: Boolean, includeOwnerFilter: Boolean): String {
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
                SUM(token_amount) 
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
