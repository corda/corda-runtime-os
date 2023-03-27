package net.corda.ledger.persistence.query.impl.parsing

interface VaultNamedQueryParser {

    fun parseWhereJson(query: String): String
}