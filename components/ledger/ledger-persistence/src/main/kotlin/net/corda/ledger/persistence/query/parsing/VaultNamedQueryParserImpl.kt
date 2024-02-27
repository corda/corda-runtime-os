package net.corda.ledger.persistence.query.parsing

import net.corda.ledger.persistence.query.parsing.converters.VaultNamedQueryConverter
import net.corda.ledger.persistence.query.parsing.expressions.VaultNamedQueryExpressionParser
import net.corda.ledger.persistence.query.parsing.expressions.VaultNamedQueryExpressionParserImpl
import net.corda.ledger.persistence.query.parsing.expressions.VaultNamedQueryExpressionValidator
import net.corda.ledger.persistence.query.parsing.expressions.VaultNamedQueryExpressionValidatorImpl
import org.osgi.service.component.annotations.Activate
import org.osgi.service.component.annotations.Component
import org.osgi.service.component.annotations.Reference

@Component(service = [ VaultNamedQueryParser::class ])
class VaultNamedQueryParserImpl(
    private val expressionParser: VaultNamedQueryExpressionParser,
    private val expressionValidator: VaultNamedQueryExpressionValidator,
    private val converter: VaultNamedQueryConverter
) : VaultNamedQueryParser {

    @Activate
    constructor(
        @Reference
        converter: VaultNamedQueryConverter
    ) : this(VaultNamedQueryExpressionParserImpl(), VaultNamedQueryExpressionValidatorImpl(), converter)

    override fun parseWhereJson(query: String): String =
        doParse(query, expressionValidator::validateWhereJson)

    override fun parseSimpleExpression(input: String): String =
        doParse(input, expressionValidator::validateSimpleExpression)

    private fun doParse(input: String, validator: (input: String, expression: List<Token>) -> List<Token>): String {
        val expression = expressionParser.parse(input)
        val clause = validator(input, expression)
        return StringBuilder().let { output ->
            converter.convert(output, clause)
            output.toString()
        }.replace("  ", " ").trim()
    }
}
