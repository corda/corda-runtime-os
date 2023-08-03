package net.corda.testing.ledger.query

import java.util.Deque
import java.util.LinkedList
import net.corda.db.hsqldb.json.HsqldbJsonExtension.JSON_SQL_TYPE
import net.corda.ledger.persistence.query.parsing.As
import net.corda.ledger.persistence.query.parsing.HasLeftParenthesis
import net.corda.ledger.persistence.query.parsing.HsqldbJsonArrayOrObjectAsText
import net.corda.ledger.persistence.query.parsing.HsqldbCast
import net.corda.ledger.persistence.query.parsing.HsqldbJsonField
import net.corda.ledger.persistence.query.parsing.HsqldbJsonKeyExists
import net.corda.ledger.persistence.query.parsing.JsonArrayOrObjectAsText
import net.corda.ledger.persistence.query.parsing.JsonCast
import net.corda.ledger.persistence.query.parsing.JsonField
import net.corda.ledger.persistence.query.parsing.JsonKeyExists
import net.corda.ledger.persistence.query.parsing.Keyword
import net.corda.ledger.persistence.query.parsing.LeftParentheses
import net.corda.ledger.persistence.query.parsing.Parameter
import net.corda.ledger.persistence.query.parsing.ParameterEnd
import net.corda.ledger.persistence.query.parsing.PathReference
import net.corda.ledger.persistence.query.parsing.PathReferenceWithSpaces
import net.corda.ledger.persistence.query.parsing.RightParentheses
import net.corda.ledger.persistence.query.parsing.Token
import net.corda.ledger.persistence.query.parsing.SqlType
import net.corda.ledger.persistence.query.parsing.expressions.AbstractVaultNamedQueryExpressionParserImpl

class HsqldbVaultNamedQueryExpressionParser : AbstractVaultNamedQueryExpressionParserImpl() {
    private companion object {
        private val JSON_TOKENS = mapOf<Token, Token>(
            JsonField() to HsqldbJsonField(),
            JsonArrayOrObjectAsText() to HsqldbJsonArrayOrObjectAsText(),
            JsonKeyExists() to HsqldbJsonKeyExists()
        )
    }

    private fun castAs(type: String): Pair<Token, Deque<Token>> {
        return Pair(HsqldbCast(), LinkedList<Token>().also { list ->
            list.add(As())
            list.add(SqlType(type))
        })
    }

    private fun removeNextExpression(tokens: Deque<Token>): LinkedList<Token> {
        val expression = LinkedList<Token>()
        if (tokens.peekFirst().let { it !is Keyword && it !is RightParentheses }) {
            var bracketCount = 0
            do {
                val token = tokens.pollFirst() ?: break

                if (token is HasLeftParenthesis) {
                    ++bracketCount
                } else if (token is RightParentheses) {
                    --bracketCount
                }

                expression.addLast(token)
            } while (bracketCount > 0)
        }
        return expression
    }

    private fun removeLastExpression(tokens: Deque<Token>): LinkedList<Token> {
        val expression = LinkedList<Token>()
        if (tokens.peekLast().let { it !is Keyword && it !is HasLeftParenthesis }) {
            var bracketCount = 0
            do {
                val token = tokens.pollLast() ?: break

                if (token is RightParentheses) {
                    ++bracketCount
                } else if (token is HasLeftParenthesis) {
                    --bracketCount
                }

                expression.addFirst(token)
            } while (bracketCount > 0)
        }
        return expression
    }

    private fun Deque<Token>.toStringLiteral(): LinkedList<Token> {
        return mapTo(LinkedList()) { token ->
            if (token is PathReference && !token.ref.startsWith('"') && !token.ref.startsWith('\'')) {
                PathReferenceWithSpaces("'${token.ref}'")
            } else {
                token
            }
        }
    }

    private fun reduceOperand(tokens: LinkedList<Token>): LinkedList<Token> {
        @Suppress("unchecked_cast")
        val reduced = tokens.clone() as LinkedList<Token>

        var leftmost: Token?
        var rightmost: Token?
        while (true) {
            leftmost = reduced.peekFirst()
            rightmost = reduced.peekLast()
            if (leftmost !is LeftParentheses || rightmost !is RightParentheses) {
                break
            }

            reduced.pollFirst()
            reduced.pollLast()
        }

        return if (leftmost === rightmost
            && (leftmost is PathReference || leftmost is Parameter)) {
            reduced
        } else {
            tokens
        }
    }

    private fun reorderJsonTokens(target: LinkedList<Token>, original: Deque<Token>): LinkedList<Token> {
        val reorderQueue = LinkedList<Pair<Token, Deque<Token>>>()
        while (true) {
            val token = original.pollFirst() ?: break
            val operation = when (token) {
                is JsonArrayOrObjectAsText,
                is JsonKeyExists,
                is JsonField ->
                    Pair(first = JSON_TOKENS[token] ?: throw IllegalStateException("Unknown JSON token '$token'"),
                        second = postProcess(removeNextExpression(original)).toStringLiteral())

                is JsonCast ->
                    castAs(token.value)

                else -> {
                    original.addFirst(token)
                    break
                }
            }
            reorderQueue.addFirst(operation)
        }

        val output = reduceOperand(target)
        if (output !== target && output.isNotEmpty() && reorderQueue.peekLast()?.first !is HsqldbCast) {
            reorderQueue.addLast(castAs(JSON_SQL_TYPE))
        }

        while (true) {
            val reorder = reorderQueue.pollLast() ?: break
            reorder.first.also { type ->
                output.addFirst(type)
                if (type !is HsqldbCast) {
                    output.addLast(ParameterEnd())
                }
            }
            output.addAll(reorder.second)
            output.addLast(RightParentheses())
        }

        return output
    }

    override fun postProcess(outputTokens: MutableList<Token>): LinkedList<Token> {
        val original = LinkedList(outputTokens)
        val processed = LinkedList<Token>()

        while (true) {
            when (original.peekFirst() ?: break) {
                is JsonCast,
                is JsonField,
                is JsonKeyExists,
                is JsonArrayOrObjectAsText ->
                    processed.addAll(reorderJsonTokens(removeLastExpression(processed), original))

                else ->
                    processed.addLast(original.pollFirst())
            }
        }

        return processed
    }
}
