package net.corda.ledger.persistence.query.parsing.converters

import net.corda.ledger.persistence.query.parsing.Token

/**
 * [VaultNamedQueryConverter] converts an expression in [Token] form into a database specific query.
 */
interface VaultNamedQueryConverter {

    /**
     * Converts an expression in [Token] form into a database specific query.
     *
     * @param output A [StringBuilder] that the output query should be appended to.
     * @param expression A list of [Token]s that represent the parsed query to be converted.
     */
    fun convert(output: StringBuilder, expression: List<Token>)
}
