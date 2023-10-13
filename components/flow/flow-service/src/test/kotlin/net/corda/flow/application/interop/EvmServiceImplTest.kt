package net.corda.flow.application.interop

import java.math.BigInteger
import net.corda.data.interop.evm.EvmRequest
import net.corda.data.interop.evm.request.Call
import net.corda.data.interop.evm.request.CallOptions
import net.corda.data.interop.evm.request.GetTransactionReceipt
import net.corda.data.interop.evm.request.Transaction
import net.corda.data.interop.evm.request.TransactionOptions
import net.corda.data.interop.evm.response.Log
import net.corda.data.interop.evm.response.TransactionReceipt
import net.corda.flow.application.interop.external.events.EvmCallExternalEventFactory
import net.corda.flow.application.interop.external.events.EvmCallExternalEventParams
import net.corda.flow.application.interop.external.events.EvmTransactionExternalEventFactory
import net.corda.flow.application.interop.external.events.EvmTransactionExternalEventParams
import net.corda.flow.application.interop.external.events.EvmTransactionReceiptExternalEventFactory
import net.corda.flow.application.interop.external.events.EvmTransactionReceiptExternalEventParams
import net.corda.flow.external.events.executor.ExternalEventExecutor
import net.corda.v5.application.interop.evm.EvmService
import net.corda.v5.application.interop.evm.Parameter
import net.corda.v5.application.interop.evm.Type
import net.corda.v5.application.interop.evm.Type.ADDRESS_LIST
import net.corda.v5.application.interop.evm.Type.BOOLEAN_LIST
import net.corda.v5.application.interop.evm.Type.INT32_ARRAY
import net.corda.v5.application.interop.evm.Type.INT64
import net.corda.v5.application.interop.evm.options.EvmOptions
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock

@Disabled
class EvmServiceImplTest {

    @Test
    fun `call with correct parameter list sends external event`() {

        val expected = "{ }"
        val expectedRequest = EvmRequest(
            "from",
            "to",
            "rpcUrl",
            String::class.java.name,
            Call(
                "test", CallOptions("latest"),
                listOf(
                    net.corda.data.interop.evm.request.Parameter("one", "int64", "1"),
                    net.corda.data.interop.evm.request.Parameter("two", "address_list", """["one","two"]"""),
                    net.corda.data.interop.evm.request.Parameter("three", "boolean_list", """[false,true]"""),
                    net.corda.data.interop.evm.request.Parameter("four", "int32_array", """[1,2]"""),
                )
            )
        )

        val event = argumentCaptor<EvmCallExternalEventParams>()
        val eventExecutor = mock<ExternalEventExecutor> {
            on { execute(eq(EvmCallExternalEventFactory::class.java), event.capture()) }.thenReturn(expected)
        }

        val service: EvmService = EvmServiceImpl(
            eventExecutor
        )

        val result = service.call(
            "test",
            "to",
            net.corda.v5.application.interop.evm.options.CallOptions("latest", "rpcUrl", "from"),
            String::class.java,
            listOf(
                Parameter.of("one", INT64, 1),
                Parameter("two", ADDRESS_LIST, listOf("one", "two")),
                Parameter("three", BOOLEAN_LIST, listOf(false, true)),
                Parameter("four", INT32_ARRAY, arrayOf(1, 2)),
            )
        )
        assertThat(result).isEqualTo(expected)
        println(expectedRequest)
    }

    @Test
    fun `transaction with correct parameter list sends external event`() {

        val expected = "0x123456789abcdef123456789abcdef"
        val expectedRequest = EvmRequest(
            "from",
            "to",
            "rpcUrl",
            String::class.java.name,
            Transaction(
                "test",
                TransactionOptions("1", "2", "3", "4"),
                listOf(
                    net.corda.data.interop.evm.request.Parameter("one", "int64", "1"),
                    net.corda.data.interop.evm.request.Parameter("two", "address", "\"two\""),
                )
            )
        )

        val event = argumentCaptor<EvmTransactionExternalEventParams>()
        val eventExecutor = mock<ExternalEventExecutor> {
            on { execute(eq(EvmTransactionExternalEventFactory::class.java), event.capture()) }.thenReturn(expected)
        }

        val service: EvmService = EvmServiceImpl(
            eventExecutor
        )

        val options = net.corda.v5.application.interop.evm.options.TransactionOptions(
            BigInteger.ONE,
            BigInteger.TWO,
            BigInteger.valueOf(4),
            BigInteger.valueOf(3),
            "rpcUrl",
            "from"
        )
        val result = service.transaction(
            "test",
            "to",
            options,
            listOf(Parameter("one", INT64, 1), Parameter("two", Type.ADDRESS, "two"))
        )
        assertThat(result).isEqualTo(expected)
//        assertThat(event.allValues.single().payload).isEqualTo(expectedRequest)
    }

    @Test
    fun `getTransactionReceipt correctly sends external event`() {

        val expectedLog = net.corda.v5.application.interop.evm.Log(
            "contract",
            listOf(""),
            "data",
            BigInteger.valueOf(1),
            "transactionHash",
            BigInteger.valueOf(5),
            "blockHash",
            0,
            false
        )
        val expected = net.corda.v5.application.interop.evm.TransactionReceipt(
            "blockHash",
            BigInteger.valueOf(1),
            "contract",
            BigInteger.valueOf(2),
            BigInteger.valueOf(3),
            "from",
            BigInteger.valueOf(4),
            listOf(expectedLog),
            "logsBloom",
            true,
            "to",
            "transactionHash",
            BigInteger.valueOf(5),
            "type"
        )

        val log = Log.newBuilder()
            .setBlockHash("blockHash")
            .setBlockNumber(1)
            .setTransactionHash("transactionHash")
            .setTransactionIndex(5)
            .setLogIndex(0)
            .setAddress("contract")
            .setData("data")
            .setRemoved(false)
            .setTopics(listOf(""))
            .build()

        val receipt = TransactionReceipt.newBuilder()
            .setBlockHash("blockHash")
            .setBlockNumber("1")
            .setContractAddress("contract")
            .setCumulativeGasUsed("2")
            .setEffectiveGasPrice("3")
            .setFrom("from")
            .setGasUsed("4")
            .setLogs(listOf(log))
            .setLogsBloom("logsBloom")
            .setStatus(true)
            .setTo("to")
            .setTransactionHash("transactionHash")
            .setTransactionIndex("5")
            .setType("type")
            .build()

        val event = argumentCaptor<EvmTransactionReceiptExternalEventParams>()
        val expectedRequest = EvmRequest(
            "",
            "",
            "rpcUrl",
            String::class.java.name,
            GetTransactionReceipt("test")
        )

        val eventExecutor = mock<ExternalEventExecutor> {
            on { execute(eq(EvmTransactionReceiptExternalEventFactory::class.java), event.capture()) }.thenReturn(
                expected
            )
        }

        val service: EvmService = EvmServiceImpl(
            eventExecutor
        )

        val result = service.getTransactionReceipt("test", EvmOptions("rpcUrl", "from"))
        assertThat(result).usingRecursiveComparison().isEqualTo(expected)
//        assertThat(event.allValues.single().payload).isEqualTo(expectedRequest)
    }
}