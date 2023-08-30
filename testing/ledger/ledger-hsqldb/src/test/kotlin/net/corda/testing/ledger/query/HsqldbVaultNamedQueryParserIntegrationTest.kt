package net.corda.testing.ledger.query

import net.corda.db.hsqldb.json.HsqldbJsonExtension.JSON_SQL_TYPE
import net.corda.ledger.persistence.query.parsing.VaultNamedQueryParserImpl
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.MethodSource
import java.util.stream.Stream

@Suppress("MaxLineLength")
class HsqldbVaultNamedQueryParserIntegrationTest {

    private val vaultNamedQueryParser = VaultNamedQueryParserImpl(HsqldbVaultNamedQueryConverter(HsqldbProvider))

    private companion object {
        private fun cast(name: String) = "CAST($name AS $JSON_SQL_TYPE)"

        @JvmStatic
        fun inputsToOutputs(): Stream<Arguments> {
            return Stream.of(
                Arguments.of("WHERE non_json_column = 'some_value'", "non_json_column = 'some_value'"),
                Arguments.of("WHERE field ->> property = 'some_value'", "JsonFieldAsText( ${cast("field")}, 'property') = 'some_value'"),
                Arguments.of("WHERE field ->> property = :value", "JsonFieldAsText( ${cast("field")}, 'property') = :value"),
                Arguments.of(
                    "WHERE \"field name\" ->> \"json property\" = 'some_value'",
                    "JsonFieldAsText(\"field name\", \"json property\") = 'some_value'"
                ),
                Arguments.of(
                    "WHERE \"field name\" -> \"json property\" ->> \"nested\" = 'some_value'",
                    "JsonFieldAsText( JsonFieldAsObject(\"field name\", \"json property\"), \"nested\") = 'some_value'"
                ),
                Arguments.of(
                    "WHERE \"field name\" -> \"json property\" -> \"nested\" ->> \"nested_more\" = 'some_value'",
                    "JsonFieldAsText( JsonFieldAsObject( JsonFieldAsObject(\"field name\", \"json property\"), \"nested\"), \"nested_more\") = 'some_value'"
                ),
                Arguments.of("WHERE (field ->> property)::int = 5", "CAST(( JsonFieldAsText( ${cast("field")}, 'property')) AS int) = 5"),
                Arguments.of("WHERE (field ->> property)::int != 5", "CAST(( JsonFieldAsText( ${cast("field")}, 'property')) AS int) != 5"),
                Arguments.of("WHERE (field ->> property)::int < 5", "CAST(( JsonFieldAsText( ${cast("field")}, 'property')) AS int) < 5"),
                Arguments.of("WHERE (field ->> property)::int <= 5", "CAST(( JsonFieldAsText( ${cast("field")}, 'property')) AS int) <= 5"),
                Arguments.of("WHERE (field ->> property)::int > 5", "CAST(( JsonFieldAsText( ${cast("field")}, 'property')) AS int) > 5"),
                Arguments.of("WHERE (field ->> property)::int >= 5", "CAST(( JsonFieldAsText( ${cast("field")}, 'property')) AS int) >= 5"),
                Arguments.of("WHERE (field ->> property)::int <= :value", "CAST(( JsonFieldAsText( ${cast("field")}, 'property')) AS int) <= :value"),
                Arguments.of("WHERE (field ->> property)::int = 1234.5678900", "CAST(( JsonFieldAsText( ${cast("field")}, 'property')) AS int) = 1234.5678900"),
                Arguments.of(
                    "WHERE field ->> property = 'some_value' AND field ->> property2 = 'another value?'",
                    "JsonFieldAsText( ${cast("field")}, 'property') = 'some_value' AND JsonFieldAsText( ${cast("field")}, 'property2') = 'another value?'"
                ),
                Arguments.of(
                    "WHERE field ->> property = 'some_value' OR field ->> property2 = 'another value'",
                    "JsonFieldAsText( ${cast("field")}, 'property') = 'some_value' OR JsonFieldAsText( ${cast("field")}, 'property2') = 'another value'"
                ),
                Arguments.of(
                    "WHERE field ->> property = 'some_value' AND field ->> property2 = 'another value' OR field ->> property3 = 'third property?'",
                    "JsonFieldAsText( ${cast("field")}, 'property') = 'some_value' AND JsonFieldAsText( ${cast("field")}, 'property2') = 'another value' OR JsonFieldAsText( ${cast("field")}, 'property3') = 'third property?'"
                ),
                Arguments.of(
                    "WHERE (field ->> property = 'some_value' AND field ->> property2 = 'another value') OR field ->> property3 = 'third property?'",
                    "( JsonFieldAsText( ${cast("field")}, 'property') = 'some_value' AND JsonFieldAsText( ${cast("field")}, 'property2') = 'another value') OR JsonFieldAsText( ${cast("field")}, 'property3') = 'third property?'"
                ),
                Arguments.of(
                    "WHERE field ->> property = 'some_value' AND (field ->> property2 = 'another value') OR field ->> property3 = 'third property'",
                    "JsonFieldAsText( ${cast("field")}, 'property') = 'some_value' AND ( JsonFieldAsText( ${cast("field")}, 'property2') = 'another value') OR JsonFieldAsText( ${cast("field")}, 'property3') = 'third property'"
                ),
                Arguments.of(
                    "WHERE (field ->> property = 'some_value' AND (field ->> property2 = 'another value') OR field ->> property3 = 'third property')",
                    "( JsonFieldAsText( ${cast("field")}, 'property') = 'some_value' AND ( JsonFieldAsText( ${cast("field")}, 'property2') = 'another value') OR JsonFieldAsText( ${cast("field")}, 'property3') = 'third property')"
                ),
                Arguments.of("WHERE field ->> property IS NULL", "JsonFieldAsText( ${cast("field")}, 'property') IS NULL"),
                Arguments.of("WHERE field ->> property IS NOT NULL", "JsonFieldAsText( ${cast("field")}, 'property') IS NOT NULL"),
                Arguments.of(
                    "WHERE field ->> property IN ('asd', 'fields value', 'asd')",
                    "JsonFieldAsText( ${cast("field")}, 'property') IN ('asd', 'fields value', 'asd')"
                ),
                Arguments.of(
                    "WHERE (field ->> property IN ('asd', 'fields value', 'asd') AND field ->> property2 = 'another value')",
                    "( JsonFieldAsText( ${cast("field")}, 'property') IN ('asd', 'fields value', 'asd') AND JsonFieldAsText( ${cast("field")}, 'property2') = 'another value')"
                ),
                Arguments.of(
                    "WHERE (field ->> property LIKE '%hello there%')",
                    "( JsonFieldAsText( ${cast("field")}, 'property') LIKE '%hello there%')"
                ),
                Arguments.of("WHERE field ? property", "HasJsonKey( ${cast("field")}, 'property')"),
                Arguments.of(
                    """
                        where
                            ("custom"->>'salary'='10'
                            and (custom ->> 'salary')::int>9.00000000
                            or custom ->> 'field with space' is null)
                    """,
                    "( JsonFieldAsText( ${cast("\"custom\"")}, 'salary') = '10' AND CAST(( JsonFieldAsText( ${cast("custom")}, 'salary')) AS int) > 9.00000000 OR JsonFieldAsText( ${cast("custom")}, 'field with space') IS NULL)"
                ),
                Arguments.of(
                    """WHERE custom -> 'TestUtxoState' ->> 'testField' = :testField
                        |AND custom -> 'Corda' ->> 'participants' IN :participants
                        |AND custom?:contractStateType
                        |AND created > :created""".trimMargin(),
                    "JsonFieldAsText( JsonFieldAsObject( ${cast("custom")}, 'TestUtxoState'), 'testField') = :testField AND JsonFieldAsText( JsonFieldAsObject( ${cast("custom")}, 'Corda'), 'participants') IN (:participants) AND HasJsonKey( ${cast("custom")}, :contractStateType) AND created > :created"
                )
            )
        }
    }

    @ParameterizedTest
    @MethodSource("inputsToOutputs")
    fun `queries are parsed from a hsqldb query and output back into a hsqldb query`(input: String, output: String) {
        assertThat(vaultNamedQueryParser.parseWhereJson(input)).isEqualTo(output)
    }

    @Test
    fun `queries containing a select throws an exception`() {
        assertThatThrownBy { vaultNamedQueryParser.parseWhereJson("SELECT field") }.isExactlyInstanceOf(IllegalArgumentException::class.java)
    }

    @Test
    fun `queries containing a from throws an exception`() {
        assertThatThrownBy { vaultNamedQueryParser.parseWhereJson("FROM table") }.isExactlyInstanceOf(IllegalArgumentException::class.java)
    }

    @Test
    fun `with json fields inside brackets`() {
        assertThat(vaultNamedQueryParser.parseWhereJson("WHERE :value = (a)->>(b)")).isEqualTo(
            ":value = JsonFieldAsText( ${cast("a")}, 'b')"
        )
    }

    @Test
    fun `cast json field to int`() {
        assertThat(vaultNamedQueryParser.parseWhereJson("WHERE a->b->>c::int = 0")).isEqualTo(
            "CAST( JsonFieldAsText( JsonFieldAsObject( ${cast("a")}, 'b'), 'c') AS int) = 0"
        )
    }

    @Test
    fun `json array index as parameter`() {
        assertThat(vaultNamedQueryParser.parseWhereJson("WHERE (a)->(b)->>(:index) = :value")).isEqualTo(
            "JsonFieldAsText( JsonFieldAsObject( ${cast("a")}, 'b'), :index) = :value"
        )
    }

    @Test
    fun `cast parameter as int`() {
        assertThat(vaultNamedQueryParser.parseWhereJson("WHERE :index::int")).isEqualTo(
            "CAST(:index AS int)"
        )

        assertThat(vaultNamedQueryParser.parseWhereJson("WHERE ((:index)::int)")).isEqualTo(
            "( CAST(:index AS int))"
        )

        assertThat(vaultNamedQueryParser.parseWhereJson("WHERE (a)->>(:index::int)")).isEqualTo(
            "JsonFieldAsText( ${cast("a")}, ( CAST(:index AS int)))"
        )

        assertThat(vaultNamedQueryParser.parseWhereJson("WHERE (a)->>((:index)::int)")).isEqualTo(
            "JsonFieldAsText( ${cast("a")}, ( CAST(:index AS int)))"
        )
    }
}
