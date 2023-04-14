package net.corda.ledger.persistence.query.impl.parsing.converters

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
import net.corda.ledger.persistence.query.parsing.converters.PostgresVaultNamedQueryConverter
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
        postgresVaultNamedQueryParser.convert(output, expression)
        assertThat(output.toString()).isEqualTo("field")
    }

    @Test
    fun `PathReferenceWithSpace is appended directly to the output with no spaces`() {
        val expression = listOf(PathReferenceWithSpaces("'field name'"))
        postgresVaultNamedQueryParser.convert(output, expression)
        assertThat(output.toString()).isEqualTo("'field name'")
    }

    @Test
    fun `Parameter is appended directly to the output with no spaces`() {
        val expression = listOf(Parameter(":parameter"))
        postgresVaultNamedQueryParser.convert(output, expression)
        assertThat(output.toString()).isEqualTo(":parameter")
    }

    @Test
    fun `Number is appended directly to the output with no spaces`() {
        val expression = listOf(Number("1"))
        postgresVaultNamedQueryParser.convert(output, expression)
        assertThat(output.toString()).isEqualTo("1")
    }

    @Test
    fun `JsonField is appended to the output with a space on either side`() {
        val expression =
            listOf(
                PATH_REFERENCE,
                JsonField(),
                PATH_REFERENCE
            )

        postgresVaultNamedQueryParser.convert(output, expression)
        assertThat(output.toString()).isEqualTo("${PATH_REFERENCE.ref} -> ${PATH_REFERENCE.ref}")
    }

    @Test
    fun `JsonArrayOrObjectAsText is appended to the output with a space on either side`() {
        val expression =
            listOf(
                PATH_REFERENCE,
                JsonArrayOrObjectAsText(),
                PATH_REFERENCE
            )

        postgresVaultNamedQueryParser.convert(output, expression)
        assertThat(output.toString()).isEqualTo("${PATH_REFERENCE.ref} ->> ${PATH_REFERENCE.ref}")
    }

    @Test
    fun `Select is appended to the output with a space on either side`() {
        val expression = listOf(PATH_REFERENCE, Select(), PATH_REFERENCE)
        postgresVaultNamedQueryParser.convert(output, expression)
        assertThat(output.toString()).isEqualTo("${PATH_REFERENCE.ref} SELECT ${PATH_REFERENCE.ref}")
    }

    @Test
    fun `As is appended to the output with a space on either side`() {
        val expression = listOf(PATH_REFERENCE, As(), PATH_REFERENCE)
        postgresVaultNamedQueryParser.convert(output, expression)
        assertThat(output.toString()).isEqualTo("${PATH_REFERENCE.ref} AS ${PATH_REFERENCE.ref}")
    }

    @Test
    fun `From is appended to the output with a space on either side`() {
        val expression = listOf(PATH_REFERENCE, From(), PATH_REFERENCE)
        postgresVaultNamedQueryParser.convert(output, expression)
        assertThat(output.toString()).isEqualTo("${PATH_REFERENCE.ref} FROM ${PATH_REFERENCE.ref}")
    }

    @Test
    fun `Where is appended to the output with a space on either side`() {
        val expression = listOf(PATH_REFERENCE, Where(), PATH_REFERENCE)
        postgresVaultNamedQueryParser.convert(output, expression)
        assertThat(output.toString()).isEqualTo("${PATH_REFERENCE.ref} WHERE ${PATH_REFERENCE.ref}")
    }

    @Test
    fun `And is appended to the output with a space on either side`() {
        val expression = listOf(PATH_REFERENCE, And(), PATH_REFERENCE)
        postgresVaultNamedQueryParser.convert(output, expression)
        assertThat(output.toString()).isEqualTo("${PATH_REFERENCE.ref} AND ${PATH_REFERENCE.ref}")
    }

    @Test
    fun `Or is appended to the output with a space on either side`() {
        val expression = listOf(PATH_REFERENCE, Or(), PATH_REFERENCE)
        postgresVaultNamedQueryParser.convert(output, expression)
        assertThat(output.toString()).isEqualTo("${PATH_REFERENCE.ref} OR ${PATH_REFERENCE.ref}")
    }

    @Test
    fun `Equals is appended to the output with a space on either side`() {
        val expression = listOf(PATH_REFERENCE, Equals(), PATH_REFERENCE)
        postgresVaultNamedQueryParser.convert(output, expression)
        assertThat(output.toString()).isEqualTo("${PATH_REFERENCE.ref} = ${PATH_REFERENCE.ref}")
    }

    @Test
    fun `NotEquals is appended to the output with a space on either side`() {
        val expression = listOf(PATH_REFERENCE, NotEquals(), PATH_REFERENCE)
        postgresVaultNamedQueryParser.convert(output, expression)
        assertThat(output.toString()).isEqualTo("${PATH_REFERENCE.ref} != ${PATH_REFERENCE.ref}")
    }

    @Test
    fun `GreaterThan is appended to the output with a space on either side`() {
        val expression = listOf(PATH_REFERENCE, GreaterThan(), PATH_REFERENCE)
        postgresVaultNamedQueryParser.convert(output, expression)
        assertThat(output.toString()).isEqualTo("${PATH_REFERENCE.ref} > ${PATH_REFERENCE.ref}")
    }

    @Test
    fun `GreaterThanEquals is appended to the output with a space on either side`() {
        val expression = listOf(PATH_REFERENCE, GreaterThanEquals(), PATH_REFERENCE)
        postgresVaultNamedQueryParser.convert(output, expression)
        assertThat(output.toString()).isEqualTo("${PATH_REFERENCE.ref} >= ${PATH_REFERENCE.ref}")
    }

    @Test
    fun `LessThan is appended to the output with a space on either side`() {
        val expression = listOf(PATH_REFERENCE, LessThan(), PATH_REFERENCE)
        postgresVaultNamedQueryParser.convert(output, expression)
        assertThat(output.toString()).isEqualTo("${PATH_REFERENCE.ref} < ${PATH_REFERENCE.ref}")
    }

    @Test
    fun `LessThanEquals is appended to the output with a space on either side`() {
        val expression = listOf(PATH_REFERENCE, LessThanEquals(), PATH_REFERENCE)
        postgresVaultNamedQueryParser.convert(output, expression)
        assertThat(output.toString()).isEqualTo("${PATH_REFERENCE.ref} <= ${PATH_REFERENCE.ref}")
    }

    @Test
    fun `In is appended to the output with a space on either side`() {
        val expression = listOf(PATH_REFERENCE, In(), PATH_REFERENCE)
        postgresVaultNamedQueryParser.convert(output, expression)
        assertThat(output.toString()).isEqualTo("${PATH_REFERENCE.ref} IN ${PATH_REFERENCE.ref}")
    }

    @Test
    fun `IsNull is appended to the output with a space on either side`() {
        val expression = listOf(PATH_REFERENCE, IsNull(), PATH_REFERENCE)
        postgresVaultNamedQueryParser.convert(output, expression)
        assertThat(output.toString()).isEqualTo("${PATH_REFERENCE.ref} IS NULL ${PATH_REFERENCE.ref}")
    }

    @Test
    fun `IsNotNull is appended to the output with a space on either side`() {
        val expression = listOf(PATH_REFERENCE, IsNotNull(), PATH_REFERENCE)
        postgresVaultNamedQueryParser.convert(output, expression)
        assertThat(output.toString()).isEqualTo("${PATH_REFERENCE.ref} IS NOT NULL ${PATH_REFERENCE.ref}")
    }

    @Test
    fun `LeftParentheses is appended directly to the output with no spaces`() {
        val expression = listOf(LeftParentheses())
        postgresVaultNamedQueryParser.convert(output, expression)
        assertThat(output.toString()).isEqualTo("(")
    }

    @Test
    fun `RightParentheses is appended directly to the output with no spaces`() {
        val expression = listOf(RightParentheses())
        postgresVaultNamedQueryParser.convert(output, expression)
        assertThat(output.toString()).isEqualTo(")")
    }

    @Test
    fun `RightParentheses removes last space to the left if previous token was a keyword`() {
        postgresVaultNamedQueryParser.convert(output, listOf(IsNull(), RightParentheses()))
        assertThat(output.toString()).isEqualTo(" IS NULL)")
        output.clear()
        postgresVaultNamedQueryParser.convert(output, listOf(IsNotNull(), RightParentheses()))
        assertThat(output.toString()).isEqualTo(" IS NOT NULL)")
    }

    @Test
    fun `JsonCast is appended directly to the output with no spaces`() {
        val expression = listOf(JsonCast("int"))
        postgresVaultNamedQueryParser.convert(output, expression)
        assertThat(output.toString()).isEqualTo("\\:\\:int")
    }

    @Test
    fun `JsonKeyExists is appended to the output with a space on either side`() {
        val expression = listOf(JsonKeyExists())
        postgresVaultNamedQueryParser.convert(output, expression)
        assertThat(output.toString()).isEqualTo(" \\?\\? ")
    }

    @Test
    fun `ParameterEnd is appended with a space on its right`() {
        val expression = listOf(ParameterEnd())
        postgresVaultNamedQueryParser.convert(output, expression)
        assertThat(output.toString()).isEqualTo(", ")
    }

    @Test
    fun `unexpected token throws an exception`() {
        val expression = listOf(object : Token {})
        assertThatThrownBy { postgresVaultNamedQueryParser.convert(output, expression) }.isInstanceOf(IllegalArgumentException::class.java)
    }

    @Test
    fun `empty expressions return an empty output`() {
        postgresVaultNamedQueryParser.convert(output, emptyList())
        assertThat(output.toString()).isEqualTo("")
    }
}