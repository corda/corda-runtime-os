package net.corda.ledger.persistence.query.parsing

interface VaultNamedQueryParser {

    fun parseWhereJson(query: String): String

    fun parseSimpleExpression(input: String): String
}
