package net.corda.ledger.persistence.query.parsing

import net.corda.ledger.persistence.query.parsing.converters.PostgresVaultNamedQueryConverter
import net.corda.ledger.persistence.query.parsing.expressions.PostgresVaultNamedQueryExpressionParser
import net.corda.ledger.persistence.query.parsing.expressions.VaultNamedQueryExpressionValidatorImpl
import net.corda.orm.DatabaseTypeProvider
import net.corda.orm.DatabaseTypeProvider.Companion.POSTGRES_TYPE_FILTER
import org.osgi.service.component.annotations.Activate
import org.osgi.service.component.annotations.Component
import org.osgi.service.component.annotations.Reference
import org.slf4j.LoggerFactory

@Suppress("unused")
@Component(service = [ VaultNamedQueryParser::class ])
class PostgresVaultNamedQueryParserImpl @Activate constructor(
    @Reference(target = POSTGRES_TYPE_FILTER)
    databaseTypeProvider: DatabaseTypeProvider
): AbstractVaultNamedQueryParserImpl(
    expressionParser = PostgresVaultNamedQueryExpressionParser(),
    expressionValidator = VaultNamedQueryExpressionValidatorImpl(),
    converter = PostgresVaultNamedQueryConverter()
) {
    init {
        LoggerFactory.getLogger(this::class.java).info("Activated for {}", databaseTypeProvider.databaseType)
    }
}
