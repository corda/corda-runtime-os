package net.corda.ledger.utxo.token.cache.queries.impl

import net.corda.ledger.utxo.token.cache.queries.SqlQueryProvider
import net.corda.v5.ledger.utxo.token.selection.Strategy
import org.osgi.service.component.annotations.Component

@Component(service = [SqlQueryProvider::class])
class SqlQueryProviderTokens : SqlQueryProvider {

    companion object {
        const val SQL_PARAMETER_TOKEN_TYPE = "tokenType"
        const val SQL_PARAMETER_ISSUER_HASH = "issuerHash"
        const val SQL_PARAMETER_SYMBOL = "symbol"
        const val SQL_PARAMETER_OWNER_HASH = "ownerHash"
        const val SQL_PARAMETER_TAG_FILTER = "tag"
        const val SQL_PARAMETER_TOKEN_NOTARY_X500_NAME = "tokenNotaryX500Name"
    }

    override fun getBalanceQuery(includeTagFilter: Boolean, includeOwnerFilter: Boolean): String {
        val tagFilter = if (includeTagFilter) {
            "AND token_tag ~ :$SQL_PARAMETER_TAG_FILTER"
        } else {
            ""
        }
        val ownerFilter = if (includeOwnerFilter) {
            "AND token_owner_hash = :$SQL_PARAMETER_OWNER_HASH"
        } else {
            ""
        }

        return """
            SELECT 
                COALESCE(SUM(token_amount), 0)
            FROM {h-schema}utxo_visible_transaction_output as t_output
            WHERE t_output.consumed is null
            AND t_output.token_type = :$SQL_PARAMETER_TOKEN_TYPE
            AND t_output.token_issuer_hash = :$SQL_PARAMETER_ISSUER_HASH
            AND t_output.token_symbol = :$SQL_PARAMETER_SYMBOL
            AND t_output.token_notary_x500_name = :$SQL_PARAMETER_TOKEN_NOTARY_X500_NAME
            $tagFilter
            $ownerFilter
        """.trimIndent()
    }

    override fun getPagedSelectQuery(limit: Int, includeTagFilter: Boolean, includeOwnerFilter: Boolean, strategy: Strategy): String {
        val tagFilter = if (includeTagFilter) {
            "AND t_output.token_tag ~ :$SQL_PARAMETER_TAG_FILTER"
        } else {
            ""
        }
        val ownerFilter = if (includeOwnerFilter) {
            "AND t_output.token_owner_hash = :$SQL_PARAMETER_OWNER_HASH"
        } else {
            ""
        }
        val orderBy = when (strategy) {
            Strategy.RANDOM -> "ORDER BY t_output.transaction_id"
            Strategy.PRIORITY -> "ORDER BY t_output.token_priority NULLS LAST, t_output.transaction_id"
        }

        // The query orders by transaction_id to create some randomness in the tokens that are selected
        return """
                 SELECT
                    t_output.transaction_id,
                    t_output.leaf_idx,
                    t_output.token_tag,
                    t_output.token_owner_hash,
                    t_output.token_amount
                FROM {h-schema}utxo_visible_transaction_output as t_output
                WHERE t_output.consumed is null 
                AND   t_output.token_type = :$SQL_PARAMETER_TOKEN_TYPE
                AND   t_output.token_issuer_hash = :$SQL_PARAMETER_ISSUER_HASH
                AND   t_output.token_symbol = :$SQL_PARAMETER_SYMBOL
                AND   t_output.token_notary_x500_name = :$SQL_PARAMETER_TOKEN_NOTARY_X500_NAME
                $tagFilter
                $ownerFilter
                $orderBy
                LIMIT $limit
        """.trimIndent()
    }
}
