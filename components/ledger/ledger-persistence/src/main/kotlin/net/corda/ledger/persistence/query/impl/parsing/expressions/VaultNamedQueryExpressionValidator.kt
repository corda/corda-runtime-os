package net.corda.ledger.persistence.query.impl.parsing.expressions

import net.corda.ledger.persistence.query.impl.parsing.Token

interface VaultNamedQueryExpressionValidator {

    fun validateWhereJson(query: String, expression: List<Token>)
}