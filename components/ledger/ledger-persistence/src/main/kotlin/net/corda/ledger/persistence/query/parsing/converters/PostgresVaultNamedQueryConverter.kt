package net.corda.ledger.persistence.query.parsing.converters

import net.corda.ledger.persistence.query.parsing.Token

class PostgresVaultNamedQueryConverter : AbstractVaultNamedQueryConverterImpl() {
    override fun customConvert(token: Token): String? = null
}
