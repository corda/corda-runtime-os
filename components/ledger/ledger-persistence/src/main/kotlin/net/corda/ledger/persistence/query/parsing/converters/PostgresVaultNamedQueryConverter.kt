package net.corda.ledger.persistence.query.parsing.converters

import net.corda.ledger.persistence.query.parsing.And
import net.corda.ledger.persistence.query.parsing.As
import net.corda.ledger.persistence.query.parsing.Equals
import net.corda.ledger.persistence.query.parsing.From
import net.corda.ledger.persistence.query.parsing.GreaterThan
import net.corda.ledger.persistence.query.parsing.GreaterThanEquals
import net.corda.ledger.persistence.query.parsing.In
import net.corda.ledger.persistence.query.parsing.IsNotNull
import net.corda.ledger.persistence.query.parsing.IsNull
import net.corda.ledger.persistence.query.parsing.JsonArrayOrObjectAsText
import net.corda.ledger.persistence.query.parsing.JsonCast
import net.corda.ledger.persistence.query.parsing.JsonField
import net.corda.ledger.persistence.query.parsing.JsonKeyExists
import net.corda.ledger.persistence.query.parsing.LeftParentheses
import net.corda.ledger.persistence.query.parsing.LessThan
import net.corda.ledger.persistence.query.parsing.LessThanEquals
import net.corda.ledger.persistence.query.parsing.Like
import net.corda.ledger.persistence.query.parsing.NotEquals
import net.corda.ledger.persistence.query.parsing.Number
import net.corda.ledger.persistence.query.parsing.Or
import net.corda.ledger.persistence.query.parsing.Parameter
import net.corda.ledger.persistence.query.parsing.ParameterEnd
import net.corda.ledger.persistence.query.parsing.PathReference
import net.corda.ledger.persistence.query.parsing.PathReferenceWithSpaces
import net.corda.ledger.persistence.query.parsing.RightParentheses
import net.corda.ledger.persistence.query.parsing.Select
import net.corda.ledger.persistence.query.parsing.Token
import net.corda.ledger.persistence.query.parsing.Where

class PostgresVaultNamedQueryConverter : VaultNamedQueryConverter {

    override fun convert(output: StringBuilder, expression: List<Token>) {
        for (token in expression) {
            when (token) {
                is PathReference -> output.append(token.ref)
                is PathReferenceWithSpaces -> output.append(token.ref)
                is Parameter -> output.append(token.ref)
                is Number -> output.append(token.ref)
                is JsonField -> output.append(" -> ")
                is JsonArrayOrObjectAsText -> output.append(" ->> ")
                is Select -> output.append(" SELECT ")
                is As -> output.append(" AS ")
                is From -> output.append(" FROM ")
                is Where -> output.append(" WHERE ")
                is And -> output.append(" AND ")
                is Or -> output.append(" OR ")
                is Equals -> output.append(" = ")
                is NotEquals -> output.append(" != ")
                is GreaterThan -> output.append(" > ")
                is GreaterThanEquals -> output.append(" >= ")
                is LessThan -> output.append(" < ")
                is LessThanEquals -> output.append(" <= ")
                is In -> output.append(" IN ")
                is IsNull -> output.append(" IS NULL ")
                is IsNotNull -> output.append(" IS NOT NULL ")
                is LeftParentheses -> output.append("(")
                is RightParentheses -> {
                    if (output.lastOrNull()?.isWhitespace() == true) {
                        output.deleteAt(output.length - 1)
                    }
                    output.append(")")
                }
                is JsonKeyExists -> output.append(" \\?\\? ")
                is JsonCast -> output.append("\\:\\:${token.value}")
                is Like -> output.append(" LIKE ")
                is ParameterEnd -> output.append(", ")
                else -> throw IllegalArgumentException("Invalid token in expression - $token")
            }
        }
    }
}