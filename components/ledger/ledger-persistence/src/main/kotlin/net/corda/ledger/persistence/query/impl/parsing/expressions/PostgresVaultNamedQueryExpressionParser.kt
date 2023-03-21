package net.corda.ledger.persistence.query.impl.parsing.expressions

import net.corda.ledger.persistence.query.impl.parsing.And
import net.corda.ledger.persistence.query.impl.parsing.As
import net.corda.ledger.persistence.query.impl.parsing.Equals
import net.corda.ledger.persistence.query.impl.parsing.From
import net.corda.ledger.persistence.query.impl.parsing.GreaterThan
import net.corda.ledger.persistence.query.impl.parsing.GreaterThanEquals
import net.corda.ledger.persistence.query.impl.parsing.In
import net.corda.ledger.persistence.query.impl.parsing.IsNotNull
import net.corda.ledger.persistence.query.impl.parsing.IsNull
import net.corda.ledger.persistence.query.impl.parsing.JsonArrayOrObjectAsText
import net.corda.ledger.persistence.query.impl.parsing.JsonCast
import net.corda.ledger.persistence.query.impl.parsing.JsonKeyExists
import net.corda.ledger.persistence.query.impl.parsing.Keyword
import net.corda.ledger.persistence.query.impl.parsing.LeftParentheses
import net.corda.ledger.persistence.query.impl.parsing.LessThan
import net.corda.ledger.persistence.query.impl.parsing.LessThanEquals
import net.corda.ledger.persistence.query.impl.parsing.Like
import net.corda.ledger.persistence.query.impl.parsing.NotEquals
import net.corda.ledger.persistence.query.impl.parsing.Number
import net.corda.ledger.persistence.query.impl.parsing.Or
import net.corda.ledger.persistence.query.impl.parsing.Parameter
import net.corda.ledger.persistence.query.impl.parsing.ParameterEnd
import net.corda.ledger.persistence.query.impl.parsing.PathReference
import net.corda.ledger.persistence.query.impl.parsing.PathReferenceWithSpaces
import net.corda.ledger.persistence.query.impl.parsing.RightParentheses
import net.corda.ledger.persistence.query.impl.parsing.Select
import net.corda.ledger.persistence.query.impl.parsing.Token
import net.corda.ledger.persistence.query.impl.parsing.Where

class PostgresVaultNamedQueryExpressionParser : VaultNamedQueryExpressionParser {
    private val stringPattern = Regex(
        """(?<str>('[^']*)'|("[^"]*)")"""
    )

    @Suppress("MaxLineLength")
    private val pathPattern = Regex(
        """(?<path>(\$?[a-zA-Z_][a-zA-Z0-9_]*(\[([0-9]+|"[a-zA-Z_][a-zA-Z0-9_]*")])?)(\.[a-zA-Z_][a-zA-Z0-9_]*(\[([0-9]+|"[a-zA-Z_][a-zA-Z0-9_]*")])?)*)"""
    )

    private val numberPattern = Regex(
        """(?<num>[0-9]+(\.[0-9]+)?([eE]-?[0-9]+)?)"""
    )

    @Suppress("MaxLineLength")
    private val opsPattern = Regex(
        """(?<op>(->>)|[+-/*=?]|<(=)?|>(=)?|==|!(=)?|(?i)\bas\b|(?i)\bfrom\b|(?i)\bselect\b|(?i)\bwhere\b|(?i)\band\b|(?i)\bor\b|(?i)\bis null\b|(?i)\bis not null\b|(?i)\bin\b|(?i)\blike\b)"""
    )

    private val jsonCastPattern = Regex("""(?<cast>::.*?)((->>)|[+*=]|&&|\|\||<(=)?|>(=)?|==|!(=)?|\s)""")

    private val parameterPattern = Regex("""(?<parameter>:[^:]\S+)""")

    @Suppress("NestedBlockDepth")
    override fun parse(query: String): List<Token> {
        val outputTokens = mutableListOf<Token>()
        var index = 0
        while (index < query.length) {
            if (query[index].isWhitespace() || query[index] == '\n') {
                ++index
                continue
            }
            val strMatch = stringPattern.find(query, index)
            if (strMatch != null && strMatch.range.first == index) {
                val str = strMatch.groups["str"]
                if (str != null) {
                    outputTokens += when {
                        " " !in str.value -> PathReference(str.value)
                        else -> PathReferenceWithSpaces(str.value)
                    }
                    index = strMatch.range.last + 1
                    continue
                }
            }
            if (query[index] == '(') {
                outputTokens += LeftParentheses()
                ++index
                continue
            }
            if (query[index] == ')') {
                outputTokens += RightParentheses()
                ++index
                continue
            }
            if (query[index] == ',') {
                outputTokens += ParameterEnd()
                ++index
                continue
            }
            val opsMatch = opsPattern.find(query, index)
            if (opsMatch != null && opsMatch.range.first == index) {
                val ops = opsMatch.groups["op"]
                if (ops != null) {
                    outputTokens += toKeyword(ops.value)
                    index = opsMatch.range.last + 1
                    continue
                }
            }
            val jsonCastMatch = jsonCastPattern.find(query, index)
            if (jsonCastMatch != null && jsonCastMatch.range.first == index) {
                val cast = jsonCastMatch.groups["cast"]
                if (cast != null) {
                    outputTokens += JsonCast(cast.value.removePrefix("::"))
                    index = cast.range.last + 1
                    continue
                }
            }
            val parameterMatch = parameterPattern.find(query, index)
            if (parameterMatch != null && parameterMatch.range.first == index) {
                val cast = parameterMatch.groups["parameter"]
                if (cast != null) {
                    outputTokens += Parameter(cast.value)
                    index = parameterMatch.range.last + 1
                    continue
                }
            }
            val pathMatch = pathPattern.find(query, index)
            if (pathMatch != null && pathMatch.range.first == index) {
                val path = pathMatch.groups["path"]
                if (path != null) {
                    outputTokens += PathReference(path.value)
                    index = pathMatch.range.last + 1
                    continue
                }
            }
            val numberMatch = numberPattern.find(query, index)
            if (numberMatch != null && numberMatch.range.first == index) {
                val number = numberMatch.groups["num"]
                if (number != null) {
                    outputTokens += Number(number.value)
                    index = numberMatch.range.last + 1
                    continue
                }
            }
            throw IllegalArgumentException("Unexpected input index: $index, value: (${query[index]}), query: ($query)")
        }
        return outputTokens
    }

    private fun toKeyword(keyword: String): Keyword {
        return when (keyword.uppercase()) {
            "!=" -> NotEquals()
            ">" -> GreaterThan()
            ">=" -> GreaterThanEquals()
            "<" -> LessThan()
            "<=" -> LessThanEquals()
            "IS NULL" -> IsNull()
            "IS NOT NULL" -> IsNotNull()
            "->>" -> JsonArrayOrObjectAsText()
            "AS" -> As()
            "FROM" -> From()
            "SELECT" -> Select()
            "WHERE" -> Where()
            "OR" -> Or()
            "AND" -> And()
            "=" -> Equals()
            "IN" -> In()
            "?" -> JsonKeyExists()
            "LIKE" -> Like()
            else -> throw IllegalArgumentException("Unknown keyword $keyword")
        }
    }
}