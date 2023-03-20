package net.corda.ledger.persistence.query.impl.parsing.converters

import net.corda.ledger.persistence.query.impl.parsing.Token

interface VaultNamedQueryConverter {

    fun convert(output: StringBuilder, expression: List<Token>): StringBuilder
}