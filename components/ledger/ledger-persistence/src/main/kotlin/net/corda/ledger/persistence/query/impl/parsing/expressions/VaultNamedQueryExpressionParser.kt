package net.corda.ledger.persistence.query.impl.parsing.expressions

import net.corda.ledger.persistence.query.impl.parsing.Token

interface VaultNamedQueryExpressionParser {

    fun parse(query: String): List<Token>
}