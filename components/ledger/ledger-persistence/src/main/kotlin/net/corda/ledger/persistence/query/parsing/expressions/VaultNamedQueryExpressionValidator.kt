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

    /**
     * Validates a simple expression (as used in an order by clause)
     *
     * @param original the original string expression
     * @param expression the expression to validate
     */
    fun validateSimpleExpression(original: String, expression: List<Token>): List<Token>
}
