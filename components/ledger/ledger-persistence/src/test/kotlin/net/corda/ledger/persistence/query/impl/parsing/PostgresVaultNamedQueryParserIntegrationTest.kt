package net.corda.ledger.persistence.query.impl.parsing

import net.corda.ledger.persistence.query.parsing.VaultNamedQueryParserImpl
import net.corda.ledger.persistence.query.parsing.converters.PostgresVaultNamedQueryConverter
import net.corda.ledger.persistence.query.parsing.expressions.PostgresVaultNamedQueryExpressionParser
import net.corda.ledger.persistence.query.parsing.expressions.VaultNamedQueryExpressionValidatorImpl
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.MethodSource
import java.util.stream.Stream

@Suppress("MaxLineLength")
class PostgresVaultNamedQueryParserIntegrationTest {

    private val vaultNamedQueryParser = VaultNamedQueryParserImpl(
        PostgresVaultNamedQueryExpressionParser(),
        VaultNamedQueryExpressionValidatorImpl(),
        PostgresVaultNamedQueryConverter()
    )

    private companion object {
        @JvmStatic
        fun inputsToOutputs(): Stream<Arguments> {
            return Stream.of(
                Arguments.of("WHERE non_json_column = 'some_value'", "WHERE non_json_column = 'some_value'"),
                Arguments.of("WHERE field ->> property = 'some_value'", "WHERE field ->> property = 'some_value'"),
                Arguments.of("WHERE field ->> property = :value", "WHERE field ->> property = :value"),
                Arguments.of(
                    "WHERE \"field name\" ->> \"json property\" = 'some_value'",
                    "WHERE \"field name\" ->> \"json property\" = 'some_value'"
                ),
                Arguments.of(
                    "WHERE \"field name\" -> \"json property\" ->> \"nested\" = 'some_value'",
                    "WHERE \"field name\" -> \"json property\" ->> \"nested\" = 'some_value'"
                ),
                Arguments.of(
                    "WHERE \"field name\" -> \"json property\" -> \"nested\" ->> \"nested_more\" = 'some_value'",
                    "WHERE \"field name\" -> \"json property\" -> \"nested\" ->> \"nested_more\" = 'some_value'"
                ),
                Arguments.of("WHERE (field ->> property)::int = 5", "WHERE (field ->> property)\\:\\:int = 5"),
                Arguments.of("WHERE (field ->> property)::int != 5", "WHERE (field ->> property)\\:\\:int != 5"),
                Arguments.of("WHERE (field ->> property)::int < 5", "WHERE (field ->> property)\\:\\:int < 5"),
                Arguments.of("WHERE (field ->> property)::int <= 5", "WHERE (field ->> property)\\:\\:int <= 5"),
                Arguments.of("WHERE (field ->> property)::int > 5", "WHERE (field ->> property)\\:\\:int > 5"),
                Arguments.of("WHERE (field ->> property)::int >= 5", "WHERE (field ->> property)\\:\\:int >= 5"),
                Arguments.of("WHERE (field ->> property)::int <= :value", "WHERE (field ->> property)\\:\\:int <= :value"),
                Arguments.of("WHERE (field ->> property)::int = 1234.5678900", "WHERE (field ->> property)\\:\\:int = 1234.5678900"),
                Arguments.of(
                    "WHERE field ->> property = 'some_value' AND field ->> property2 = 'another value?'",
                    "WHERE field ->> property = 'some_value' AND field ->> property2 = 'another value?'"
                ),
                Arguments.of(
                    "WHERE field ->> property = 'some_value' OR field ->> property2 = 'another value'",
                    "WHERE field ->> property = 'some_value' OR field ->> property2 = 'another value'"
                ),
                Arguments.of(
                    "WHERE field ->> property = 'some_value' AND field ->> property2 = 'another value' OR field ->> property3 = 'third property?'",
                    "WHERE field ->> property = 'some_value' AND field ->> property2 = 'another value' OR field ->> property3 = 'third property?'"
                ),
                Arguments.of(
                    "WHERE (field ->> property = 'some_value' AND field ->> property2 = 'another value') OR field ->> property3 = 'third property?'",
                    "WHERE (field ->> property = 'some_value' AND field ->> property2 = 'another value') OR field ->> property3 = 'third property?'"
                ),
                Arguments.of(
                    "WHERE field ->> property = 'some_value' AND (field ->> property2 = 'another value') OR field ->> property3 = 'third property')",
                    "WHERE field ->> property = 'some_value' AND (field ->> property2 = 'another value') OR field ->> property3 = 'third property')"
                ),
                Arguments.of(
                    "WHERE (field ->> property = 'some_value' AND (field ->> property2 = 'another value') OR field ->> property3 = 'third property'))",
                    "WHERE (field ->> property = 'some_value' AND (field ->> property2 = 'another value') OR field ->> property3 = 'third property'))"
                ),
                Arguments.of("WHERE field ->> property IS NULL", "WHERE field ->> property IS NULL"),
                Arguments.of("WHERE field ->> property IS NOT NULL", "WHERE field ->> property IS NOT NULL"),
                Arguments.of(
                    "WHERE field ->> property IN ('asd', 'fields value', 'asd')",
                    "WHERE field ->> property IN ('asd', 'fields value', 'asd')"
                ),
                Arguments.of(
                    "WHERE (field ->> property IN ('asd', 'fields value', 'asd') AND field ->> property2 = 'another value')",
                    "WHERE (field ->> property IN ('asd', 'fields value', 'asd') AND field ->> property2 = 'another value')"
                ),
                Arguments.of(
                    "WHERE (field ->> property LIKE '%hello there%')",
                    "WHERE (field ->> property LIKE '%hello there%')"
                ),
                Arguments.of("WHERE field ? property", "WHERE field \\?\\? property"),
                Arguments.of(
                    """
                        where
                            ("custom"->>'salary'='10'
                            and (custom ->> 'salary')::int>9.00000000
                            or custom ->> 'field with space' is null)
                    """,
                    "WHERE (\"custom\" ->> 'salary' = '10' AND (custom ->> 'salary')\\:\\:int > 9.00000000 OR custom ->> 'field with space' IS NULL)"
                ),
                Arguments.of(
                    """WHERE custom -> 'TestUtxoState' ->> 'testField' = :testField
                        |AND custom -> 'Corda' ->> 'participants' IN :participants
                        |AND custom?:contractStateType
                        |AND created > :created""".trimMargin(),
                    "WHERE custom -> 'TestUtxoState' ->> 'testField' = :testField AND custom -> 'Corda' ->> 'participants' IN :participants AND custom \\?\\? :contractStateType AND created > :created"
                )
            )
        }
    }

    @ParameterizedTest
    @MethodSource("inputsToOutputs")
    fun `queries are parsed from a postgres query and output back into a postgres query`(input: String, output: String) {
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
}