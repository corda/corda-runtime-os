package net.corda.ledger.persistence.query.parsing.expressions

import net.corda.ledger.persistence.query.parsing.And
import net.corda.ledger.persistence.query.parsing.As
import net.corda.ledger.persistence.query.parsing.Associativity.Left
import net.corda.ledger.persistence.query.parsing.BinaryKeyword
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
import net.corda.ledger.persistence.query.parsing.Keyword
import net.corda.ledger.persistence.query.parsing.LeftParenthesis
import net.corda.ledger.persistence.query.parsing.LessThan
import net.corda.ledger.persistence.query.parsing.LessThanEquals
import net.corda.ledger.persistence.query.parsing.Like
import net.corda.ledger.persistence.query.parsing.NotEquals
import net.corda.ledger.persistence.query.parsing.NotIn
import net.corda.ledger.persistence.query.parsing.NotLike
import net.corda.ledger.persistence.query.parsing.Number
import net.corda.ledger.persistence.query.parsing.Operator
import net.corda.ledger.persistence.query.parsing.Or
import net.corda.ledger.persistence.query.parsing.Parameter
import net.corda.ledger.persistence.query.parsing.ParameterEnd
import net.corda.ledger.persistence.query.parsing.PathReference
import net.corda.ledger.persistence.query.parsing.PathReferenceWithSpaces
import net.corda.ledger.persistence.query.parsing.Reference
import net.corda.ledger.persistence.query.parsing.RightParenthesis
import net.corda.ledger.persistence.query.parsing.Select
import net.corda.ledger.persistence.query.parsing.SqlType
import net.corda.ledger.persistence.query.parsing.Token
import net.corda.ledger.persistence.query.parsing.TopLevelKeyword
import net.corda.ledger.persistence.query.parsing.UnaryKeyword
import net.corda.ledger.persistence.query.parsing.Where
import java.util.Deque
import java.util.LinkedList

class VaultNamedQueryExpressionParserImpl : VaultNamedQueryExpressionParser {
    private companion object {
        private val stringPattern = """(?<str>('[^']*)'|("[^"]*)")""".toRegex()

        @Suppress("MaxLineLength")
        private val pathPattern =
            """(?<path>(\$?[a-zA-Z_][a-zA-Z0-9_]*(\[([0-9]+|"[a-zA-Z_][a-zA-Z0-9_]*")])?)(\.[a-zA-Z_][a-zA-Z0-9_]*(\[([0-9]+|"[a-zA-Z_][a-zA-Z0-9_]*")])?)*)""".toRegex()

        private val numberPattern = """(?<num>[0-9]+(\.[0-9]+)?([eE]-?[0-9]+)?)""".toRegex()

        @Suppress("MaxLineLength")
        private val opsPattern =
            """(?<op>(->>)|(->)|[+-/*=?]|<(=)?|>(=)?|==|!(=)?|(?i)\bas\b|(?i)\bfrom\b|(?i)\bselect\b|(?i)\bwhere\b|(?i)\band\b|(?i)\bor\b|(?i)\bis\s++null\b|(?i)\bis\s++not\s++null\b|(?i)\bin\b|(?i)\bnot\s++in\b|(?i)\blike\b|(?i)\bnot\s++like\b)""".toRegex()

        private val jsonCastPattern = """::(?<cast>.*?)((->>)|[+*=()\s]|&&|\|\||<(=)?|>(=)?|==|!(=)?|$)""".toRegex()

        private val parameterPattern = """(?<parameter>:[\w-]+)""".toRegex()

        private val whitespacePattern = """\s++""".toRegex()

        private fun parenthesise(tokens: LinkedList<Token>): LinkedList<Token> {
            if (tokens.peekFirst() != LeftParenthesis ||
                tokens.peekLast() != RightParenthesis ||
                tokens.lastIndexOf(LeftParenthesis) != 0
            ) {
                tokens.addFirst(LeftParenthesis)
                tokens.addLast(RightParenthesis)
            }
            return tokens
        }
    }

    /**
     * A sub-sequence of [Token]s extracted from between parentheses.
     */
    private class Expression(val ops: LinkedList<Token>) : Token {
        fun tokens(): LinkedList<Token> {
            return if (ops.size == 1 && ops.peekFirst() is Reference) {
                ops
            } else {
                LinkedList(ops).apply {
                    addFirst(LeftParenthesis)
                    addLast(RightParenthesis)
                }
            }
        }
    }

    @Suppress("NestedBlockDepth", "ThrowsCount")
    private fun tokenize(query: String): LinkedList<Token> {
        val parentheses = LinkedList<LinkedList<Token>>()
        var output = LinkedList<Token>()
        var index = 0
        while (index < query.length) {
            if (query[index].isWhitespace()) {
                ++index
                continue
            }

            val strMatch = stringPattern.matchAt(query, index)
            if (strMatch != null) {
                val str = strMatch.groups["str"]
                if (str != null) {
                    output += when {
                        whitespacePattern.containsMatchIn(str.value) -> PathReferenceWithSpaces(str.value)
                        else -> PathReference(str.value)
                    }
                    index = strMatch.range.last + 1
                    continue
                }
            }

            if (query[index] == '(') {
                parentheses.addFirst(output)
                output = LinkedList()
                ++index
                continue
            }

            if (query[index] == ')') {
                val previous = parentheses.pollFirst()
                if (previous == null) {
                    throw IllegalArgumentException("Unbalanced right parenthesis at index $index for query: $query")
                } else if (previous.isNotEmpty() || output.isEmpty() || parentheses.isEmpty()) {
                    previous += Expression(output)
                    output = previous
                }
                ++index
                continue
            }

            if (query[index] == ',') {
                output += ParameterEnd
                ++index
                continue
            }

            val opsMatch = opsPattern.matchAt(query, index)
            if (opsMatch != null) {
                val ops = opsMatch.groups["op"]
                if (ops != null) {
                    output += toKeyword(ops.value)
                    index = opsMatch.range.last + 1
                    continue
                }
            }

            val jsonCastMatch = jsonCastPattern.matchAt(query, index)
            if (jsonCastMatch != null) {
                val cast = jsonCastMatch.groups["cast"]
                if (cast != null) {
                    output += JsonCast()
                    output += SqlType(cast.value)
                    index = cast.range.last + 1
                    continue
                }
            }

            val parameterMatch = parameterPattern.matchAt(query, index)
            if (parameterMatch != null) {
                val cast = parameterMatch.groups["parameter"]
                if (cast != null) {
                    output += Parameter(cast.value)
                    index = parameterMatch.range.last + 1
                    continue
                }
            }

            val pathMatch = pathPattern.matchAt(query, index)
            if (pathMatch != null) {
                val path = pathMatch.groups["path"]
                if (path != null) {
                    output += PathReference(path.value)
                    index = pathMatch.range.last + 1
                    continue
                }
            }

            val numberMatch = numberPattern.matchAt(query, index)
            if (numberMatch != null) {
                val number = numberMatch.groups["num"]
                if (number != null) {
                    output += Number(number.value)
                    index = numberMatch.range.last + 1
                    continue
                }
            }

            throw IllegalArgumentException("Unexpected input index: $index, value: (${query[index]}), query: ($query)")
        }

        if (parentheses.isNotEmpty()) {
            throw IllegalArgumentException("Unbalanced left parentheses for query: $query")
        }

        return output
    }

    private fun toKeyword(keyword: String): Keyword {
        return when (val name = whitespacePattern.replace(keyword, " ").uppercase()) {
            "SELECT" -> Select()
            "WHERE" -> Where()
            "FROM" -> From()
            "OR" -> Or()
            "AND" -> And()
            "=" -> Equals()
            "!=" -> NotEquals()
            ">" -> GreaterThan()
            ">=" -> GreaterThanEquals()
            "<" -> LessThan()
            "<=" -> LessThanEquals()
            "->" -> JsonField()
            "->>" -> JsonArrayOrObjectAsText()
            "::" -> JsonCast()
            "?" -> JsonKeyExists()
            "AS" -> As()
            "IS NULL" -> IsNull()
            "IS NOT NULL" -> IsNotNull()
            "IN" -> In()
            "NOT IN" -> NotIn()
            "LIKE" -> Like()
            "NOT LIKE" -> NotLike()
            else ->
                throw IllegalArgumentException("Unknown SQL keyword '$name'")
        }
    }

    private fun removeTopLevelTokens(input: Deque<Token>): LinkedList<Token> {
        val expression = LinkedList<Token>()
        while (input.peekFirst().let { it != null && (it !is TopLevelKeyword || expression.peekLast() is ParameterEnd) }) {
            expression.addLast(input.pollFirst())
        }
        return expression
    }

    private fun removeExpression(output: Deque<Token>): LinkedList<Token> {
        return when (val op = output.pollLast()) {
            is Expression ->
                op.tokens()

            null ->
                throw IllegalArgumentException("Not enough operands for expression.")

            else ->
                LinkedList<Token>().also { list ->
                    list.addFirst(op)
                }
        }
    }

    private fun createOperation(operators: Deque<Operator>, output: Deque<Token>): Token {
        return when (val operator = operators.removeFirst()) {
            is BinaryKeyword -> {
                val op2 = when (operator) {
                    is In, is NotIn ->
                        parenthesise(removeExpression(output))

                    else ->
                        removeExpression(output)
                }
                val op1 = removeExpression(output)
                operator.create(op1, op2)
            }

            is UnaryKeyword ->
                operator.create(removeExpression(output))

            else ->
                throw IllegalArgumentException("Unexpected operator ${operator::class.java.simpleName}")
        }
    }

    private fun expandExpressions(input: Deque<Token>): LinkedList<Token> {
        val output = LinkedList<Token>()
        for (token in input) {
            if (token is Expression) {
                output.addAll(token.tokens())
            } else {
                output.add(token)
            }
        }
        return output
    }

    @Suppress("NestedBlockDepth")
    private fun parse(input: Deque<Token>): LinkedList<Token> {
        val output = LinkedList<Token>()
        val operators = LinkedList<Operator>()
        while (true) {
            val operation = when (val token = input.pollFirst() ?: break) {
                is TopLevelKeyword ->
                    token.create(parse(removeTopLevelTokens(input)))

                // Use Shunting Yard Algorithm to create operator tree.
                is Operator -> {
                    while (true) {
                        val top = operators.peekFirst() ?: break
                        if ((token.precedence > top.precedence) ||
                            (token.precedence == top.precedence && token.associativity == Left)
                        ) {
                            output.addLast(createOperation(operators, output))
                        } else {
                            break
                        }
                    }
                    operators.addFirst(token)
                    continue
                }

                is ParameterEnd -> {
                    while (operators.isNotEmpty()) {
                        output.addLast(createOperation(operators, output))
                    }
                    token
                }

                is Expression ->
                    Expression(parse(token.ops))

                is Reference ->
                    token

                else ->
                    throw IllegalArgumentException("Unrecognised element '$token'")
            }

            output.addLast(operation)
        }

        while (operators.isNotEmpty()) {
            output.addLast(createOperation(operators, output))
        }

        return expandExpressions(output)
    }

    /**
     * Convert the SQL query string into list of [Token] trees.
     */
    override fun parse(query: String): List<Token> {
        return parse(tokenize(query))
    }
}
