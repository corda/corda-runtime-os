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

    override fun parseWhereJson(query: String): String {
        val expression = expressionParser.parse(query)
        val whereClause = expressionValidator.validateWhereJson(query, expression)
        return StringBuilder().let { output ->
            converter.convert(output, whereClause)
            output.toString()
        }.replace("  ", " ").trim()
    }
}
