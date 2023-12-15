package net.corda.ledger.persistence.query.impl.parsing.expressions

import net.corda.ledger.persistence.query.parsing.BinaryKeyword
import net.corda.ledger.persistence.query.parsing.Keyword
import net.corda.ledger.persistence.query.parsing.Reference
import net.corda.ledger.persistence.query.parsing.Token
import net.corda.ledger.persistence.query.parsing.UnaryKeyword
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.assertAll

fun assertMatches(actual: Collection<Token>, expected: Collection<Token>) {
    assertMatches(MatchContext.start(), actual, expected)
}

private fun assertMatches(context: MatchContext, actual: Collection<Token>, expected: Collection<Token>) {
    assertEquals(expected.size, actual.size, "$context: Expected $expected but was $actual")
    val expectedIterator = expected.iterator()
    val actualIterator = actual.iterator()
    while (expectedIterator.hasNext() && actualIterator.hasNext()) {
        val expectedToken = expectedIterator.next()
        val actualToken = actualIterator.next()

        assertSame(expectedToken::class.java, actualToken::class.java) {
            "$context: Expected ${expectedToken::class.java.simpleName} but was ${actualToken::class.java.simpleName}"
        }

        when (expectedToken) {
            is BinaryKeyword ->
                assertAll(
                    { assertMatches(context.push(expectedToken), (actualToken as BinaryKeyword).op1, expectedToken.op1) },
                    { assertMatches(context.push(expectedToken), (actualToken as BinaryKeyword).op2, expectedToken.op2) }
                )

            is UnaryKeyword ->
                assertMatches(context.push(expectedToken), (actualToken as UnaryKeyword).op, expectedToken.op)

            is Reference ->
                assertEquals(expectedToken.ref, (actualToken as Reference).ref, "$context: '$actualToken' does not match '$expectedToken'")
        }
    }
}

private class MatchContext private constructor(
    private val parent: MatchContext?,
    private val keyword: Keyword?
) {
    companion object {
        fun start(): MatchContext {
            return MatchContext(null, null)
        }
    }

    fun push(keyword: Keyword): MatchContext {
        return MatchContext(this, keyword)
    }

    override fun toString(): String {
        val builder = StringBuilder()
        var current = this
        while (true) {
            builder.append(current.keyword?.let { it::class.java.simpleName } ?: "ROOT")
            current = current.parent ?: break
            builder.append("->")
        }
        return builder.toString()
    }
}
