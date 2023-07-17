package net.corda.ledger.persistence.query.parsing

import net.corda.ledger.persistence.query.parsing.converters.VaultNamedQueryConverter
import net.corda.ledger.persistence.query.parsing.expressions.VaultNamedQueryExpressionParser
import net.corda.ledger.persistence.query.parsing.expressions.VaultNamedQueryExpressionValidator

abstract class AbstractVaultNamedQueryParserImpl(
    private val expressionParser: VaultNamedQueryExpressionParser,
    private val expressionValidator: VaultNamedQueryExpressionValidator,
    private val converter: VaultNamedQueryConverter
) : VaultNamedQueryParser {

    final override fun parseWhereJson(query: String): String {
        val expression = expressionParser.parse(query)
        expressionValidator.validateWhereJson(query, expression)
        val output = StringBuilder()
        converter.convert(output, expression)
        return output.toString()
            .replace("  ", " ")
            .trim()
    }
}
