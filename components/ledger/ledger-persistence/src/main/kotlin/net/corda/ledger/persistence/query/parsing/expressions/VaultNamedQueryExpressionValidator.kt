package net.corda.ledger.persistence.query.parsing.expressions

import net.corda.ledger.persistence.query.parsing.Token

/**
 * [VaultNamedQueryExpressionValidator] validates an expression.
 */
interface VaultNamedQueryExpressionValidator {

    /**
     * Validates a `whereJson` expression.
     *
     * @param query The original query before it was parsed.
     * @param expression The parsed expression to validate.
     */
    fun validateWhereJson(query: String, expression: List<Token>): List<Token>
}