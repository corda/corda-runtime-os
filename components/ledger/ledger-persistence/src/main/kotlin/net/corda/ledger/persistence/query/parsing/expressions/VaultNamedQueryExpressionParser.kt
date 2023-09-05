package net.corda.ledger.persistence.query.parsing.expressions

import net.corda.ledger.persistence.query.parsing.Token

/**
 * [VaultNamedQueryExpressionParser] parses query strings into an expression of [Token]s that can be later converted into a database
 * specific query string.
 */
interface VaultNamedQueryExpressionParser {

    /**
     * Parses a query string into an expression of [Token]s.
     *
     * @param query The query to parse.
     *
     * @return A list of [Token]s representing the parsed query.
     */
    fun parse(query: String): List<Token>
}
