package net.corda.libs.statemanager.impl.repository.impl

import net.corda.db.schema.DbSchema
import net.corda.libs.statemanager.api.Operation
import net.corda.libs.statemanager.impl.model.v1.StateEntity
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.MethodSource
import org.mockito.kotlin.KArgumentCaptor
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import java.util.stream.Stream
import javax.persistence.EntityManager

class StateRepositoryImplTest {
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

    private val stateRepository = StateRepositoryImpl()

    private val sqlCaptor: KArgumentCaptor<String> = argumentCaptor()
    private val entityManager: EntityManager = mock {
        on { createNativeQuery(any(), eq(StateEntity::class.java)) } doReturn mock()
    }

    @ParameterizedTest
    @MethodSource("operations")
    fun filterByMetadataUsesCorrectOperation(operation: Pair<Operation, String>) {
        val key = "key1"
        val value = "value1"

        stateRepository.filterByMetadata(entityManager, key, operation.first, value)
        verify(entityManager).createNativeQuery(sqlCaptor.capture(), eq(StateEntity::class.java))
        assertThat(sqlCaptor.firstValue).isEqualToNormalizingWhitespace(
            "SELECT s.key, s.value, s.metadata, s.version, s.modified_time " +
                    "FROM ${DbSchema.STATE_MANAGER_TABLE} s " +
                    "WHERE (s.metadata->>'$key')::::text ${operation.second} '$value'"
        )
    }

    @ParameterizedTest
    @MethodSource("types")
    fun filterByMetadataUsesCorrectType(type: Pair<Any, String>) {
        val key = "key1"

        stateRepository.filterByMetadata(entityManager, key, Operation.Equals, type.first)
        verify(entityManager).createNativeQuery(sqlCaptor.capture(), eq(StateEntity::class.java))
        assertThat(sqlCaptor.firstValue).isEqualToNormalizingWhitespace(
            "SELECT s.key, s.value, s.metadata, s.version, s.modified_time " +
                    "FROM ${DbSchema.STATE_MANAGER_TABLE} s " +
                    "WHERE (s.metadata->>'$key')::::${type.second} = '${type.first}'"
        )
    }
}
