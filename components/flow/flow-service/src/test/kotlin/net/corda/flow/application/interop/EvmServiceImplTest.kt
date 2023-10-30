package net.corda.flow.application.interop

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import java.math.BigInteger
import net.corda.flow.TestMarshallingService
import net.corda.flow.application.interop.external.events.EvmCallExternalEventFactory
import net.corda.flow.external.events.executor.ExternalEventExecutor
import net.corda.v5.application.interop.evm.EvmService
import net.corda.v5.application.interop.evm.Parameter
import net.corda.v5.application.interop.evm.Type
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.MethodSource
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class EvmServiceImplTest {

    private fun callParams() =
            listOf(
                Arguments.of("test", Type.STRING),
                Arguments.of(1, Type.INT32),
                Arguments.of(1L, Type.INT64),
                Arguments.of(BigInteger.ONE, Type.UINT256),
                Arguments.of(true, Type.BOOLEAN),
                Arguments.of(listOf(true, false), Type.BOOLEAN_LIST),
                Arguments.of(arrayOf(true, false), Type.BOOLEAN_ARRAY),
                Arguments.of(listOf(1L, 2L, 3L), Type.INT64_LIST),
                Arguments.of(arrayOf(1L, 2L, 3L), Type.INT64_ARRAY),
                Arguments.of(listOf(BigInteger.valueOf(1L), BigInteger.valueOf(2L), BigInteger.valueOf(3L)), Type.UINT256_LIST),
                Arguments.of(arrayOf(BigInteger.valueOf(1L), BigInteger.valueOf(2L), BigInteger.valueOf(3L)), Type.UINT256_ARRAY),
            )

    @ParameterizedTest
    @MethodSource("callParams")
    fun `call result is correctly deserialized`(value: Any, type: Type<*>) {
        val expected = jacksonObjectMapper().writeValueAsString(value)
        val eventExecutor = mock<ExternalEventExecutor> {
            on { execute(eq(EvmCallExternalEventFactory::class.java), any()) }.thenReturn(expected)
        }
        val service: EvmService = EvmServiceImpl(TestMarshallingService(), eventExecutor)
        assertThat(service.call("function", "to", mock(), type, Parameter.of("name", ""))).isEqualTo(value)
    }
}