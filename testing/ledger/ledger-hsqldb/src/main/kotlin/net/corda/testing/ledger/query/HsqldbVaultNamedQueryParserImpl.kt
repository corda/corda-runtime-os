package net.corda.testing.ledger.query

import net.corda.ledger.persistence.query.parsing.AbstractVaultNamedQueryParserImpl
import net.corda.ledger.persistence.query.parsing.VaultNamedQueryParser
import net.corda.ledger.persistence.query.parsing.expressions.VaultNamedQueryExpressionValidatorImpl
import net.corda.orm.DatabaseTypeProvider
import net.corda.orm.DatabaseTypeProvider.Companion.HSQLDB_TYPE_FILTER
import org.osgi.service.component.annotations.Activate
import org.osgi.service.component.annotations.Component
import org.osgi.service.component.annotations.Reference
import org.slf4j.LoggerFactory

@Suppress("unused")
@Component(service = [ VaultNamedQueryParser::class ])
class HsqldbVaultNamedQueryParserImpl @Activate constructor(
    @Reference(target = HSQLDB_TYPE_FILTER)
    databaseTypeProvider: DatabaseTypeProvider
): AbstractVaultNamedQueryParserImpl(
    expressionParser = HsqldbVaultNamedQueryExpressionParser(),
    expressionValidator = VaultNamedQueryExpressionValidatorImpl(),
    converter = HsqldbVaultNamedQueryConverter()
) {
    init {
        LoggerFactory.getLogger(this::class.java).info("Activated for {}", databaseTypeProvider.databaseType)
    }
}
