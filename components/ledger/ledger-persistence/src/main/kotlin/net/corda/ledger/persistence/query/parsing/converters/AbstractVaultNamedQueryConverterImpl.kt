package net.corda.ledger.persistence.query.parsing.converters

import net.corda.ledger.persistence.query.parsing.And
import net.corda.ledger.persistence.query.parsing.As
import net.corda.ledger.persistence.query.parsing.BinaryKeyword
import net.corda.ledger.persistence.query.parsing.Equals
import net.corda.ledger.persistence.query.parsing.From
import net.corda.ledger.persistence.query.parsing.GreaterThan
import net.corda.ledger.persistence.query.parsing.GreaterThanEquals
import net.corda.ledger.persistence.query.parsing.In
import net.corda.ledger.persistence.query.parsing.IsNotNull
import net.corda.ledger.persistence.query.parsing.IsNull
import net.corda.ledger.persistence.query.parsing.LeftParenthesis
import net.corda.ledger.persistence.query.parsing.LessThan
import net.corda.ledger.persistence.query.parsing.LessThanEquals
import net.corda.ledger.persistence.query.parsing.Like
import net.corda.ledger.persistence.query.parsing.NotEquals
import net.corda.ledger.persistence.query.parsing.NotIn
import net.corda.ledger.persistence.query.parsing.NotLike
import net.corda.ledger.persistence.query.parsing.Or
import net.corda.ledger.persistence.query.parsing.ParameterEnd
import net.corda.ledger.persistence.query.parsing.Reference
import net.corda.ledger.persistence.query.parsing.RightParenthesis
import net.corda.ledger.persistence.query.parsing.Select
import net.corda.ledger.persistence.query.parsing.Token
import net.corda.ledger.persistence.query.parsing.UnaryKeyword
import net.corda.ledger.persistence.query.parsing.Where

@Suppress("MemberVisibilityCanBePrivate")
abstract class AbstractVaultNamedQueryConverterImpl : VaultNamedQueryConverter {

    protected open fun writeCustom(output: StringBuilder, token: Token) {
        throw IllegalArgumentException("Invalid token in expression - $token")
    }

    protected fun writePrefixOperator(output: StringBuilder, text: String, unary: UnaryKeyword) {
        output.append(text)
        write(output, unary.op)
    }

    protected fun writePostfixOperator(output: StringBuilder, text: String, unary: UnaryKeyword) {
        write(output, unary.op)
        output.append(text)
    }

    protected fun writeBinaryOperator(output: StringBuilder, text: String, binary: BinaryKeyword) {
        write(output, binary.op1)
        output.append(text)
        write(output, binary.op2)
    }

    protected fun write(output: StringBuilder, tokens: Iterable<Token>) {
        for (token in tokens) {
            write(output, token)
        }
    }

    protected fun write(output: StringBuilder, token: Token) {
        when (token) {
            is Select -> writePrefixOperator(output, " SELECT ", token)
            is From -> writePrefixOperator(output, " FROM ", token)
            is Where -> writePrefixOperator(output, " WHERE ", token)
            is Equals -> writeBinaryOperator(output, " = ", token)
            is NotEquals -> writeBinaryOperator(output, " != ", token)
            is GreaterThan -> writeBinaryOperator(output, " > ", token)
            is GreaterThanEquals -> writeBinaryOperator(output, " >= ", token)
            is LessThan -> writeBinaryOperator(output, " < ", token)
            is LessThanEquals -> writeBinaryOperator(output, " <= ", token)
            is And -> writeBinaryOperator(output, " AND ", token)
            is Or -> writeBinaryOperator(output, " OR ", token)
            is IsNull -> writePostfixOperator(output, " IS NULL ", token)
            is IsNotNull -> writePostfixOperator(output, " IS NOT NULL ", token)
            is Like -> writeBinaryOperator(output, " LIKE ", token)
            is NotLike -> writeBinaryOperator(output, " NOT LIKE ", token)
            is In -> writeBinaryOperator(output, " IN ", token)
            is NotIn -> writeBinaryOperator(output, " NOT IN ", token)
            is As -> writeBinaryOperator(output, " AS ", token)
            is Reference -> output.append(token.ref)
            LeftParenthesis -> output.append('(')
            RightParenthesis -> {
                if (output.lastOrNull()?.isWhitespace() == true) {
                    output.deleteAt(output.length - 1)
                }
                output.append(')')
            }
            ParameterEnd -> output.append(", ")
            else ->
                writeCustom(output, token)
        }
    }

    final override fun convert(output: StringBuilder, expression: List<Token>) {
        write(output, expression)
    }
}
