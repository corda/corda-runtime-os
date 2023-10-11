package net.r3.corda.web3j.encoder

import net.corda.data.interop.evm.request.Parameter
import net.corda.interop.evm.encoder.TransactionEncoder
import org.assertj.core.api.Assertions
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.MethodSource
import org.web3j.abi.FunctionEncoder
import org.web3j.utils.Numeric


class TypeEncoderTests {

    private val transactionEncoder = TransactionEncoder()
    private val setter = "setter"
    private val type = "type"


    private fun compareEncoding(input: Any, inputType: String): Pair<String, String> {

        val stringifiedInput = input.toString()

        val parameters = listOf(
            Parameter(type, inputType, stringifiedInput)
        )
        val evmServiceEncodedTransaction = transactionEncoder.encode(setter, parameters)
        val web3input = if (inputType.contains("bytes")) {
            Numeric.hexStringToByteArray(stringifiedInput)

        } else {
            input
        }
        val web3jFunction = FunctionEncoder.makeFunction(
            setter,
            listOf(inputType),
            listOf(web3input),
            emptyList()
        )
        val web3jEncodedTransaction = FunctionEncoder.encode(web3jFunction)
        return evmServiceEncodedTransaction to web3jEncodedTransaction
    }

    companion object {
        private const val addressInput = "0xC900BD2233B9596E5589446CC494c1EE70D12F67"
        private const val booleanInput = true
        private const val numericInput = 10
        private const val stringInput = "Hello World"

        @JvmStatic
        private fun testTypes(): List<Pair<String, Any>> {
            val (uintList, intList) = (8..256 step 8).map {
                Pair(
                    Pair("uint$it", numericInput),
                    Pair("int$it", numericInput)
                )
            }.unzip()

            val othersList = listOf(
                Pair("address", addressInput),
                Pair("bool", booleanInput),
                Pair("string", stringInput)
            )
            val base = "0x"
            val bytesList = (1..32).map {
                val output = StringBuilder(base)
                (1..it).map {
                    output.append("00")
                }
                Pair("bytes$it", output)
            }
            return uintList + intList + othersList + bytesList
        }
    }

    @ParameterizedTest
    @MethodSource("testTypes")
    fun `try different encodings`(input: Pair<String, Any>) {
        val (inputType, value) = input
        val (cordaEncoding, web3jEncoding) = compareEncoding(value, inputType)
        Assertions.assertThat(cordaEncoding).isEqualTo(web3jEncoding)
    }


}









