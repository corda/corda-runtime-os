package net.corda.ledger.persistence.query.parsing.expressions

import net.corda.ledger.persistence.query.parsing.BinaryKeyword
import net.corda.ledger.persistence.query.parsing.From
import net.corda.ledger.persistence.query.parsing.Select
import net.corda.ledger.persistence.query.parsing.Token
import net.corda.ledger.persistence.query.parsing.UnaryKeyword

class VaultNamedQueryExpressionValidatorImpl : VaultNamedQueryExpressionValidator {

    override fun validateWhereJson(query: String, expression: List<Token>) {
        Validator(query).validate(expression)
    }

    private class Validator(private val query: String) {
        fun validate(expression: Iterable<Token>) {
            for (token in expression) {
                when (token) {
                    is Select -> throw exception("SELECT")
                    is From -> throw exception("FROM")
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
