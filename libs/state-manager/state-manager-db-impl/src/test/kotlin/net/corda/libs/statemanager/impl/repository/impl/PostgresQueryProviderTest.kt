package net.corda.libs.statemanager.impl.repository.impl

import net.corda.libs.statemanager.api.MetadataFilter
import net.corda.libs.statemanager.api.Operation
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.MethodSource
import java.util.stream.Stream

@Disabled
class PostgresQueryProviderTest {
    companion object {
        @JvmStatic
        fun operations(): Stream<Arguments> {
            return Stream.of(
                Arguments.of(Pair(Operation.Equals, "=")),
                Arguments.of(Pair(Operation.NotEquals, "<>")),
                Arguments.of(Pair(Operation.LesserThan, "<")),
                Arguments.of(Pair(Operation.GreaterThan, ">")),
            )
        }

        @JvmStatic
        fun types(): Stream<Arguments> {
            return Stream.of(
                Arguments.of(Pair(5, "numeric")),
                Arguments.of(Pair(5.4, "numeric")),
                Arguments.of(Pair(100f, "numeric")),
                Arguments.of(Pair("string", "text")),
                Arguments.of(Pair(true, "boolean")),
                Arguments.of(Pair(false, "boolean")),
            )
        }
    }

    private val queryProvider = PostgresQueryProvider()

    @ParameterizedTest
    @MethodSource("operations")
    fun metadataKeyFilterUsesCorrectOperation(operation: Pair<Operation, String>) {
        val key = "key1"
        val value = "value1"
        val sqlQuery = queryProvider.metadataKeyFilter(MetadataFilter(key, operation.first, value))

        assertThat(sqlQuery)
            .isEqualToNormalizingWhitespace("(s.metadata->>'$key')::text ${operation.second} '$value'")
    }

    @ParameterizedTest
    @MethodSource("types")
    fun metadataKeyFilterUsesCorrectType(type: Pair<Any, String>) {
        val key = "key1"
        val sqlQuery = queryProvider.metadataKeyFilter(MetadataFilter(key, Operation.Equals, type.first))

        assertThat(sqlQuery)
            .isEqualToNormalizingWhitespace("(s.metadata->>'$key')::${type.second} = '${type.first}'")
    }

    @Test
    fun metadataKeyFiltersBuildsCorrectExpressions() {
        val sqlQuery = queryProvider.metadataKeyFilters(
            listOf(
                MetadataFilter("key1", Operation.Equals, "text"),
                MetadataFilter("key2", Operation.GreaterThan, 10),
                MetadataFilter("key3", Operation.NotEquals, true),
            )
        )

        assertThat(sqlQuery).containsExactly(
            "((s.metadata->>'key1')::text = 'text')",
            "((s.metadata->>'key2')::numeric > '10')",
            "((s.metadata->>'key3')::boolean <> 'true')",
        )
    }
}
