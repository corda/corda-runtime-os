package net.corda.testing.ledger.query

import net.corda.db.hsqldb.json.HsqldbJsonExtension.JSON_SQL_TYPE
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
import net.corda.ledger.persistence.query.parsing.LeftParenthesis
import net.corda.ledger.persistence.query.parsing.LessThan
import net.corda.ledger.persistence.query.parsing.LessThanEquals
import net.corda.ledger.persistence.query.parsing.NotEquals
import net.corda.ledger.persistence.query.parsing.Number
import net.corda.ledger.persistence.query.parsing.Or
import net.corda.ledger.persistence.query.parsing.Parameter
import net.corda.ledger.persistence.query.parsing.ParameterEnd
import net.corda.ledger.persistence.query.parsing.PathReference
import net.corda.ledger.persistence.query.parsing.PathReferenceWithSpaces
import net.corda.ledger.persistence.query.parsing.RightParenthesis
import net.corda.ledger.persistence.query.parsing.Select
import net.corda.ledger.persistence.query.parsing.SqlType
import net.corda.ledger.persistence.query.parsing.Token
import net.corda.ledger.persistence.query.parsing.Where
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test

class HsqldbVaultNamedQueryConverterTest {

    private companion object {
        private val PATH_A = PathReference("fieldA")
        private val PATH_B = PathReference("fieldB")

        private fun cast(name: String) = "CAST($name AS $JSON_SQL_TYPE)"
    }

    private val vaultNamedQueryConverter = HsqldbVaultNamedQueryConverter(HsqldbProvider)

    private val output = StringBuilder()

    @Test
    fun `PathReference is appended directly to the output with no spaces`() {
        val expression = listOf(PATH_A)
        vaultNamedQueryConverter.convert(output, expression)
        assertThat(output.toString()).isEqualTo("fieldA")
    }

    @Test
    fun `PathReferenceWithSpace is appended directly to the output with no spaces`() {
        val expression = listOf(PathReferenceWithSpaces("'field name'"))
        vaultNamedQueryConverter.convert(output, expression)
        assertThat(output.toString()).isEqualTo("'field name'")
    }

    @Test
    fun `Parameter is appended directly to the output with no spaces`() {
        val expression = listOf(Parameter(":parameter"))
        vaultNamedQueryConverter.convert(output, expression)
        assertThat(output.toString()).isEqualTo(":parameter")
    }

    @Test
    fun `Number is appended directly to the output with no spaces`() {
        val expression = listOf(Number("1"))
        vaultNamedQueryConverter.convert(output, expression)
        assertThat(output.toString()).isEqualTo("1")
    }

    @Test
    fun `JsonField is appended to the output with a space on left side`() {
        val expression = listOf(JsonField(
            listOf(PATH_A),
            listOf(PATH_B)
        ))

        vaultNamedQueryConverter.convert(output, expression)
        assertThat(output.toString()).isEqualTo(" JsonFieldAsObject( ${cast(PATH_A.ref)}, '${PATH_B.ref}')")
    }

    @Test
    fun `JsonArrayOrObjectAsText is appended to the output with a space on left side`() {
        val expression = listOf(
            JsonArrayOrObjectAsText(
                listOf(PATH_A),
                listOf(PATH_B)
            )
        )

        vaultNamedQueryConverter.convert(output, expression)
        assertThat(output.toString()).isEqualTo(" JsonFieldAsText( ${cast(PATH_A.ref)}, '${PATH_B.ref}')")
    }

    @Test
    fun `Select is appended to the output with a space on either side`() {
        val expression = listOf(PATH_A, Select(listOf(PATH_B)))
        vaultNamedQueryConverter.convert(output, expression)
        assertThat(output.toString()).isEqualTo("${PATH_A.ref} SELECT ${PATH_B.ref}")
    }

    @Test
    fun `As is appended to the output with a space on either side`() {
        val expression = listOf(As(
            listOf(PATH_A),
            listOf(PATH_B)
        ))
        vaultNamedQueryConverter.convert(output, expression)
        assertThat(output.toString()).isEqualTo("${PATH_A.ref} AS ${PATH_B.ref}")
    }

    @Test
    fun `From is appended to the output with a space on either side`() {
        val expression = listOf(PATH_A, From(listOf(PATH_B)))
        vaultNamedQueryConverter.convert(output, expression)
        assertThat(output.toString()).isEqualTo("${PATH_A.ref} FROM ${PATH_B.ref}")
    }

    @Test
    fun `Where is appended to the output with a space on either side`() {
        val expression = listOf(PATH_A, Where(listOf(PATH_B)))
        vaultNamedQueryConverter.convert(output, expression)
        assertThat(output.toString()).isEqualTo("${PATH_A.ref} WHERE ${PATH_B.ref}")
    }

    @Test
    fun `And is appended to the output with a space on either side`() {
        val expression = listOf(And(
            listOf(PATH_A),
            listOf(PATH_B)
        ))
        vaultNamedQueryConverter.convert(output, expression)
        assertThat(output.toString()).isEqualTo("${PATH_A.ref} AND ${PATH_B.ref}")
    }

    @Test
    fun `Or is appended to the output with a space on either side`() {
        val expression = listOf(Or(
            listOf(PATH_A),
            listOf(PATH_B)
        ))
        vaultNamedQueryConverter.convert(output, expression)
        assertThat(output.toString()).isEqualTo("${PATH_A.ref} OR ${PATH_B.ref}")
    }

    @Test
    fun `Equals is appended to the output with a space on either side`() {
        val expression = listOf(Equals(
            listOf(PATH_A),
            listOf(PATH_B)
        ))
        vaultNamedQueryConverter.convert(output, expression)
        assertThat(output.toString()).isEqualTo("${PATH_A.ref} = ${PATH_B.ref}")
    }

    @Test
    fun `NotEquals is appended to the output with a space on either side`() {
        val expression = listOf(NotEquals(
            listOf(PATH_A),
            listOf(PATH_B)
        ))
        vaultNamedQueryConverter.convert(output, expression)
        assertThat(output.toString()).isEqualTo("${PATH_A.ref} != ${PATH_B.ref}")
    }

    @Test
    fun `GreaterThan is appended to the output with a space on either side`() {
        val expression = listOf(GreaterThan(
            listOf(PATH_A),
            listOf(PATH_B)
        ))
        vaultNamedQueryConverter.convert(output, expression)
        assertThat(output.toString()).isEqualTo("${PATH_A.ref} > ${PATH_B.ref}")
    }

    @Test
    fun `GreaterThanEquals is appended to the output with a space on either side`() {
        val expression = listOf(GreaterThanEquals(
            listOf(PATH_A),
            listOf(PATH_B)
        ))
        vaultNamedQueryConverter.convert(output, expression)
        assertThat(output.toString()).isEqualTo("${PATH_A.ref} >= ${PATH_B.ref}")
    }

    @Test
    fun `LessThan is appended to the output with a space on either side`() {
        val expression = listOf(LessThan(
            listOf(PATH_A),
            listOf(PATH_B)
        ))
        vaultNamedQueryConverter.convert(output, expression)
        assertThat(output.toString()).isEqualTo("${PATH_A.ref} < ${PATH_B.ref}")
    }

    @Test
    fun `LessThanEquals is appended to the output with a space on either side`() {
        val expression = listOf(LessThanEquals(
            listOf(PATH_A),
            listOf(PATH_B)
        ))
        vaultNamedQueryConverter.convert(output, expression)
        assertThat(output.toString()).isEqualTo("${PATH_A.ref} <= ${PATH_B.ref}")
    }

    @Test
    fun `In is appended to the output with a space on either side`() {
        val expression = listOf(In(
            listOf(PATH_A),
            listOf(PATH_B)
        ))
        vaultNamedQueryConverter.convert(output, expression)
        assertThat(output.toString()).isEqualTo("${PATH_A.ref} IN ${PATH_B.ref}")
    }

    @Test
    fun `IsNull is appended to the output with a space on either side`() {
        val expression = listOf(IsNull(
            listOf(PATH_A)
        ), PATH_B)
        vaultNamedQueryConverter.convert(output, expression)
        assertThat(output.toString()).isEqualTo("${PATH_A.ref} IS NULL ${PATH_B.ref}")
    }

    @Test
    fun `IsNotNull is appended to the output with a space on either side`() {
        val expression = listOf(IsNotNull(
            listOf(PATH_A)
        ), PATH_B)
        vaultNamedQueryConverter.convert(output, expression)
        assertThat(output.toString()).isEqualTo("${PATH_A.ref} IS NOT NULL ${PATH_B.ref}")
    }

    @Test
    fun `LeftParentheses is appended directly to the output with no spaces`() {
        val expression = listOf(LeftParenthesis)
        vaultNamedQueryConverter.convert(output, expression)
        assertThat(output.toString()).isEqualTo("(")
    }

    @Test
    fun `RightParentheses is appended directly to the output with no spaces`() {
        val expression = listOf(RightParenthesis)
        vaultNamedQueryConverter.convert(output, expression)
        assertThat(output.toString()).isEqualTo(")")
    }

    @Test
    fun `RightParentheses removes last space to the left if previous token was a keyword`() {
        vaultNamedQueryConverter.convert(output, listOf(
            IsNull(listOf(PATH_A)), RightParenthesis
        ))
        assertThat(output.toString()).isEqualTo("${PATH_A.ref} IS NULL)")
        output.clear()

        vaultNamedQueryConverter.convert(output, listOf(
            IsNotNull(listOf(PATH_A)), RightParenthesis
        ))
        assertThat(output.toString()).isEqualTo("${PATH_A.ref} IS NOT NULL)")
    }

    @Test
    fun `JsonCast is appended directly to the output with a space on left side`() {
        val expression = listOf(JsonCast(
            listOf(PATH_A),
            listOf(SqlType("int"))
        ))
        vaultNamedQueryConverter.convert(output, expression)
        assertThat(output.toString()).isEqualTo(" CAST(${PATH_A.ref} AS int)")
    }

    @Test
    fun `JsonKeyExists is appended to the output with a space on left side`() {
        val expression = listOf(JsonKeyExists(
            listOf(PATH_A),
            listOf(PATH_B)
        ))
        vaultNamedQueryConverter.convert(output, expression)
        assertThat(output.toString()).isEqualTo(" HasJsonKey( ${cast(PATH_A.ref)}, '${PATH_B.ref}')")
    }

    @Test
    fun `ParameterEnd is appended with a space on its right`() {
        val expression = listOf(ParameterEnd)
        vaultNamedQueryConverter.convert(output, expression)
        assertThat(output.toString()).isEqualTo(", ")
    }

    @Test
    fun `unexpected token throws an exception`() {
        val expression = listOf(object : Token {})
        assertThatThrownBy { vaultNamedQueryConverter.convert(output, expression) }.isInstanceOf(IllegalArgumentException::class.java)
    }

    @Test
    fun `empty expressions return an empty output`() {
        vaultNamedQueryConverter.convert(output, emptyList())
        assertThat(output.toString()).isEqualTo("")
    }
}
