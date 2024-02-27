package net.corda.ledger.persistence.query.parsing.converters

import net.corda.ledger.persistence.query.parsing.JsonArrayOrObjectAsText
import net.corda.ledger.persistence.query.parsing.JsonCast
import net.corda.ledger.persistence.query.parsing.JsonField
import net.corda.ledger.persistence.query.parsing.JsonKeyExists
import net.corda.ledger.persistence.query.parsing.Token
import net.corda.orm.DatabaseTypeProvider
import net.corda.orm.DatabaseTypeProvider.Companion.POSTGRES_TYPE_FILTER
import net.corda.utilities.debug
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
        LoggerFactory.getLogger(this::class.java).debug { "Activated for ${databaseTypeProvider.databaseType}" }
    }

    override fun writeCustom(output: StringBuilder, token: Token) {
        when (token) {
            is JsonField -> writeBinaryOperator(output, " -> ", token)
            is JsonArrayOrObjectAsText -> writeBinaryOperator(output, " ->> ", token)
            is JsonCast -> writeBinaryOperator(output, "\\:\\:", token)
            is JsonKeyExists -> writeBinaryOperator(output, " \\?\\? ", token)
            else ->
                super.writeCustom(output, token)
        }
    }
}
