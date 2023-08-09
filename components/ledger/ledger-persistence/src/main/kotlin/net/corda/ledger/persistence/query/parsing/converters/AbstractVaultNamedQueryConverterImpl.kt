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
import net.corda.ledger.persistence.query.parsing.JsonField
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
import net.corda.ledger.persistence.query.parsing.SqlType
import net.corda.ledger.persistence.query.parsing.Token
import net.corda.ledger.persistence.query.parsing.Where

abstract class AbstractVaultNamedQueryConverterImpl : VaultNamedQueryConverter {

    /**
     * Extra processing of the expression tokens before rewriting them as SQL.
     */
    protected abstract fun preProcess(outputTokens: List<Token>): List<Token>

    /**
     * Conversion rules for extra database-specific tokens.
     */
    protected abstract fun customConvert(token: Token): String?

    private fun convert(token: Token): String {
        return when (token) {
            is PathReference -> token.ref
            is PathReferenceWithSpaces -> token.ref
            is Parameter -> token.ref
            is Number -> token.ref
            is SqlType -> token.type
            is JsonField -> " -> "
            is JsonArrayOrObjectAsText -> " ->> "
            is Select -> " SELECT "
            is As -> " AS "
            is From -> " FROM "
            is Where -> " WHERE "
            is And -> " AND "
            is Or -> " OR "
            is Equals -> " = "
            is NotEquals -> " != "
            is GreaterThan -> " > "
            is GreaterThanEquals -> " >= "
            is LessThan -> " < "
            is LessThanEquals -> " <= "
            is In -> " IN "
            is IsNull -> " IS NULL "
            is IsNotNull -> " IS NOT NULL "
            is LeftParentheses -> "("
            is RightParentheses -> ")"
            is Like -> " LIKE "
            is ParameterEnd -> ", "
            else -> customConvert(token) ?: throw IllegalArgumentException("Invalid token in expression - $token")
        }
    }

    final override fun convert(output: StringBuilder, expression: List<Token>) {
        for (token in preProcess(expression)) {
            if (token is RightParentheses) {
                if (output.lastOrNull()?.isWhitespace() == true) {
                    output.deleteAt(output.length - 1)
                }
            }
            output.append(convert(token))
        }
    }
}
