package net.corda.ledger.persistence.query.impl.parsing.converters

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
import net.corda.ledger.persistence.query.impl.parsing.LeftParentheses
import net.corda.ledger.persistence.query.impl.parsing.LessThan
import net.corda.ledger.persistence.query.impl.parsing.LessThanEquals
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
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test

class PostgresVaultNamedQueryConverterTest {

    private companion object {
        val PATH_REFERENCE = PathReference("field")
    }

    private val postgresVaultNamedQueryParser = PostgresVaultNamedQueryConverter()

    private val output = StringBuilder("")

    @Test
    fun `PathReference is appended directly to the output with no spaces`() {
        val expression = listOf(PATH_REFERENCE)
        assertThat(postgresVaultNamedQueryParser.convert(output, expression).toString()).isEqualTo("field")
    }

    @Test
    fun `PathReferenceWithSpace is appended directly to the output with no spaces`() {
        val expression = listOf(PathReferenceWithSpaces("'field name'"))
        assertThat(postgresVaultNamedQueryParser.convert(output, expression).toString()).isEqualTo("'field name'")
    }

    @Test
    fun `Parameter is appended directly to the output with no spaces`() {
        val expression = listOf(Parameter(":parameter"))
        assertThat(postgresVaultNamedQueryParser.convert(output, expression).toString()).isEqualTo(":parameter")
    }

    @Test
    fun `Number is appended directly to the output with no spaces`() {
        val expression = listOf(Number("1"))
        assertThat(postgresVaultNamedQueryParser.convert(output, expression).toString()).isEqualTo("1")
    }

    @Test
    fun `JsonArrayOrObjectAsText is appended to the output with a space on either side`() {
        val expression =
            listOf(
                PATH_REFERENCE,
                JsonArrayOrObjectAsText(),
                PATH_REFERENCE
            )

        assertThat(
            postgresVaultNamedQueryParser.convert(output, expression)
                .toString()
        ).isEqualTo("${PATH_REFERENCE.ref} ->> ${PATH_REFERENCE.ref}")
    }

    @Test
    fun `Select is appended to the output with a space on either side`() {
        val expression = listOf(PATH_REFERENCE, Select(), PATH_REFERENCE)
        assertThat(
            postgresVaultNamedQueryParser.convert(output, expression)
                .toString()
        ).isEqualTo("${PATH_REFERENCE.ref} SELECT ${PATH_REFERENCE.ref}")
    }

    @Test
    fun `As is appended to the output with a space on either side`() {
        val expression = listOf(PATH_REFERENCE, As(), PATH_REFERENCE)
        assertThat(
            postgresVaultNamedQueryParser.convert(output, expression)
                .toString()
        ).isEqualTo("${PATH_REFERENCE.ref} AS ${PATH_REFERENCE.ref}")
    }

    @Test
    fun `From is appended to the output with a space on either side`() {
        val expression = listOf(PATH_REFERENCE, From(), PATH_REFERENCE)
        assertThat(
            postgresVaultNamedQueryParser.convert(output, expression)
                .toString()
        ).isEqualTo("${PATH_REFERENCE.ref} FROM ${PATH_REFERENCE.ref}")
    }

    @Test
    fun `Where is appended to the output with a space on either side`() {
        val expression = listOf(PATH_REFERENCE, Where(), PATH_REFERENCE)
        assertThat(
            postgresVaultNamedQueryParser.convert(output, expression)
                .toString()
        ).isEqualTo("${PATH_REFERENCE.ref} WHERE ${PATH_REFERENCE.ref}")
    }

    @Test
    fun `And is appended to the output with a space on either side`() {
        val expression = listOf(PATH_REFERENCE, And(), PATH_REFERENCE)
        assertThat(
            postgresVaultNamedQueryParser.convert(output, expression)
                .toString()
        ).isEqualTo("${PATH_REFERENCE.ref} AND ${PATH_REFERENCE.ref}")
    }

    @Test
    fun `Or is appended to the output with a space on either side`() {
        val expression = listOf(PATH_REFERENCE, Or(), PATH_REFERENCE)
        assertThat(
            postgresVaultNamedQueryParser.convert(output, expression)
                .toString()
        ).isEqualTo("${PATH_REFERENCE.ref} OR ${PATH_REFERENCE.ref}")
    }

    @Test
    fun `Equals is appended to the output with a space on either side`() {
        val expression = listOf(PATH_REFERENCE, Equals(), PATH_REFERENCE)
        assertThat(
            postgresVaultNamedQueryParser.convert(output, expression)
                .toString()
        ).isEqualTo("${PATH_REFERENCE.ref} = ${PATH_REFERENCE.ref}")
    }

    @Test
    fun `NotEquals is appended to the output with a space on either side`() {
        val expression = listOf(PATH_REFERENCE, NotEquals(), PATH_REFERENCE)
        assertThat(
            postgresVaultNamedQueryParser.convert(output, expression)
                .toString()
        ).isEqualTo("${PATH_REFERENCE.ref} != ${PATH_REFERENCE.ref}")
    }

    @Test
    fun `GreaterThan is appended to the output with a space on either side`() {
        val expression = listOf(PATH_REFERENCE, GreaterThan(), PATH_REFERENCE)
        assertThat(
            postgresVaultNamedQueryParser.convert(output, expression)
                .toString()
        ).isEqualTo("${PATH_REFERENCE.ref} > ${PATH_REFERENCE.ref}")
    }

    @Test
    fun `GreaterThanEquals is appended to the output with a space on either side`() {
        val expression = listOf(PATH_REFERENCE, GreaterThanEquals(), PATH_REFERENCE)
        assertThat(
            postgresVaultNamedQueryParser.convert(output, expression)
                .toString()
        ).isEqualTo("${PATH_REFERENCE.ref} >= ${PATH_REFERENCE.ref}")
    }

    @Test
    fun `LessThan is appended to the output with a space on either side`() {
        val expression = listOf(PATH_REFERENCE, LessThan(), PATH_REFERENCE)
        assertThat(
            postgresVaultNamedQueryParser.convert(output, expression)
                .toString()
        ).isEqualTo("${PATH_REFERENCE.ref} < ${PATH_REFERENCE.ref}")
    }

    @Test
    fun `LessThanEquals is appended to the output with a space on either side`() {
        val expression = listOf(PATH_REFERENCE, LessThanEquals(), PATH_REFERENCE)
        assertThat(
            postgresVaultNamedQueryParser.convert(output, expression)
                .toString()
        ).isEqualTo("${PATH_REFERENCE.ref} <= ${PATH_REFERENCE.ref}")
    }

    @Test
    fun `In is appended to the output with a space on either side`() {
        val expression = listOf(PATH_REFERENCE, In(), PATH_REFERENCE)
        assertThat(
            postgresVaultNamedQueryParser.convert(output, expression)
                .toString()
        ).isEqualTo("${PATH_REFERENCE.ref} IN ${PATH_REFERENCE.ref}")
    }

    @Test
    fun `IsNull is appended to the output with a space on either side`() {
        val expression = listOf(PATH_REFERENCE, IsNull(), PATH_REFERENCE)
        assertThat(
            postgresVaultNamedQueryParser.convert(output, expression)
                .toString()
        ).isEqualTo("${PATH_REFERENCE.ref} IS NULL ${PATH_REFERENCE.ref}")
    }

    @Test
    fun `IsNotNull is appended to the output with a space on either side`() {
        val expression = listOf(PATH_REFERENCE, IsNotNull(), PATH_REFERENCE)
        assertThat(
            postgresVaultNamedQueryParser.convert(output, expression)
                .toString()
        ).isEqualTo("${PATH_REFERENCE.ref} IS NOT NULL ${PATH_REFERENCE.ref}")
    }

    @Test
    fun `LeftParentheses is appended directly to the output with no spaces`() {
        val expression = listOf(LeftParentheses())
        assertThat(postgresVaultNamedQueryParser.convert(output, expression).toString()).isEqualTo("(")
    }

    @Test
    fun `RightParentheses is appended directly to the output with no spaces`() {
        val expression = listOf(RightParentheses())
        assertThat(postgresVaultNamedQueryParser.convert(output, expression).toString()).isEqualTo(")")
    }

    @Test
    fun `RightParentheses removes last space to the left if previous token was a keyword`() {
        val expression1 = listOf(IsNull(), RightParentheses())
        val expression2 = listOf(IsNotNull(), RightParentheses())
        assertThat(postgresVaultNamedQueryParser.convert(output, expression1).toString()).isEqualTo(" IS NULL)")
        output.clear()
        assertThat(postgresVaultNamedQueryParser.convert(output, expression2).toString()).isEqualTo(" IS NOT NULL)")
    }

    @Test
    fun `JsonCast is appended directly to the output with no spaces`() {
        val expression = listOf(JsonCast("int"))
        assertThat(postgresVaultNamedQueryParser.convert(output, expression).toString()).isEqualTo("::int")
    }

    @Test
    fun `JsonKeyExists is appended to the output with a space on either side`() {
        val expression = listOf(JsonKeyExists())
        assertThat(postgresVaultNamedQueryParser.convert(output, expression).toString()).isEqualTo(" ? ")
    }

    @Test
    fun `ParameterEnd is appended with a space on its right`() {
        val expression = listOf(ParameterEnd())
        assertThat(postgresVaultNamedQueryParser.convert(output, expression).toString()).isEqualTo(", ")
    }

    @Test
    fun `unexpected token throws an exception`() {
        val expression = listOf(object : Token {})
        assertThatThrownBy { postgresVaultNamedQueryParser.convert(output, expression) }.isInstanceOf(IllegalArgumentException::class.java)
    }

    @Test
    fun `empty expressions return an empty output`() {
        assertThat(postgresVaultNamedQueryParser.convert(output, emptyList()).toString()).isEqualTo("")
    }
}