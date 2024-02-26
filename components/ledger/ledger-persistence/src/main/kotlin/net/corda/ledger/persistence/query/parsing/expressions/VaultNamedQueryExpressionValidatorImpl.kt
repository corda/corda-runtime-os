package net.corda.ledger.persistence.query.parsing.expressions

import net.corda.ledger.persistence.query.parsing.BinaryKeyword
import net.corda.ledger.persistence.query.parsing.From
import net.corda.ledger.persistence.query.parsing.Select
import net.corda.ledger.persistence.query.parsing.Token
import net.corda.ledger.persistence.query.parsing.UnaryKeyword
import net.corda.ledger.persistence.query.parsing.Where

class VaultNamedQueryExpressionValidatorImpl : VaultNamedQueryExpressionValidator {

    override fun validateWhereJson(query: String, expression: List<Token>): List<Token> {
        val where = expression.firstOrNull() as? Where
        if (where == null || expression.size != 1) {
            throw IllegalArgumentException("Vault named query '$query' should contain a single WHERE clause.")
        }
        return where.op.also { condition ->
            Validator(query).validate(condition)
        }
    }

    override fun validateSimpleExpression(original: String, expression: List<Token>): List<Token> {
        Validator(original).validate(expression)
        return expression
    }

    private class Validator(private val query: String) {
        @Suppress("ThrowsCount")
        fun validate(expression: Iterable<Token>) {
            for (token in expression) {
                when (token) {
                    is Select -> throw exception("SELECT")
                    is From -> throw exception("FROM")
                    is Where -> throw exception("WHERE")
                    is UnaryKeyword -> validate(token.op)
                    is BinaryKeyword -> {
                        validate(token.op1)
                        validate(token.op2)
                    }
                }
            }
        }

        private fun exception(keyword: String): IllegalArgumentException {
            return IllegalArgumentException("Vault named queries cannot contain the $keyword keyword. Query: $query")
        }
    }
}
