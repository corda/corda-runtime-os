package net.corda.ledger.persistence.query.impl.parsing

import net.corda.ledger.persistence.query.impl.parsing.converters.VaultNamedQueryConverter
import net.corda.ledger.persistence.query.impl.parsing.expressions.VaultNamedQueryExpressionParser
import net.corda.ledger.persistence.query.impl.parsing.expressions.VaultNamedQueryExpressionValidator

class VaultNamedQueryParserImpl(
    private val expressionParser: VaultNamedQueryExpressionParser,
    private val expressionValidator: VaultNamedQueryExpressionValidator,
    private val converter: VaultNamedQueryConverter
) : VaultNamedQueryParser {

    override fun parseWhereJson(query: String): String {
        val expression = expressionParser.parse(query)
        expressionValidator.validateWhereJson(query, expression)
        val output = StringBuilder("")
        converter.convert(output, expression)
        return output.toString()
            .replace("  ", " ")
            .trim()
    }
}