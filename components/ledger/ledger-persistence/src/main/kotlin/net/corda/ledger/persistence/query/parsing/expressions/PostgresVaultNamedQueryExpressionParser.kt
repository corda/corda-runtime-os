package net.corda.ledger.persistence.query.parsing.expressions

import net.corda.ledger.persistence.query.parsing.Token

class PostgresVaultNamedQueryExpressionParser : AbstractVaultNamedQueryExpressionParserImpl() {
    override fun postProcess(outputTokens: MutableList<Token>): MutableList<Token> {
        return outputTokens
    }
}
