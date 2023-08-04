package net.corda.ledger.persistence.query.parsing.converters

import net.corda.ledger.persistence.query.parsing.Token
import net.corda.orm.DatabaseTypeProvider
import net.corda.orm.DatabaseTypeProvider.Companion.POSTGRES_TYPE_FILTER
import org.osgi.service.component.annotations.Activate
import org.osgi.service.component.annotations.Component
import org.osgi.service.component.annotations.Reference
import org.slf4j.LoggerFactory

@Component(service = [ VaultNamedQueryConverter::class ])
class PostgresVaultNamedQueryConverter @Activate constructor(
    @Reference(target = POSTGRES_TYPE_FILTER)
    databaseTypeProvider: DatabaseTypeProvider
) : AbstractVaultNamedQueryConverterImpl() {
    init {
        LoggerFactory.getLogger(this::class.java).info("Activated for {}", databaseTypeProvider.databaseType)
    }

    override fun preProcess(outputTokens: List<Token>) = outputTokens
    override fun customConvert(token: Token): String? = null
}
