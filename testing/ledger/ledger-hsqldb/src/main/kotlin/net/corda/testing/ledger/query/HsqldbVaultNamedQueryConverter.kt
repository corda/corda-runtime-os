package net.corda.testing.ledger.query

import net.corda.ledger.persistence.query.parsing.HsqldbJsonArrayOrObjectAsText
import net.corda.ledger.persistence.query.parsing.HsqldbCast
import net.corda.ledger.persistence.query.parsing.HsqldbJsonField
import net.corda.ledger.persistence.query.parsing.HsqldbJsonKeyExists
import net.corda.ledger.persistence.query.parsing.Token
import net.corda.ledger.persistence.query.parsing.converters.AbstractVaultNamedQueryConverterImpl

class HsqldbVaultNamedQueryConverter : AbstractVaultNamedQueryConverterImpl() {
    override fun customConvert(token: Token): String? {
        return when (token) {
            is HsqldbJsonField -> " JsonFieldAsObject("
            is HsqldbJsonArrayOrObjectAsText -> " JsonFieldAsText("
            is HsqldbJsonKeyExists -> " HasJsonKey("
            is HsqldbCast -> " CAST("
            else -> null
        }
    }
}
