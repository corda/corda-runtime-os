package net.r3.corda.web3j.encoder

import net.corda.data.interop.evm.request.Parameter
import net.corda.interop.evm.encoder.TransactionEncoder
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

import org.web3j.abi.FunctionEncoder
import org.assertj.core.api.Assertions.assertThat


class TransactionEncoderTest {

    private lateinit var transactionEncoder: TransactionEncoder

    @BeforeEach
    fun setUp() {
        transactionEncoder = TransactionEncoder()
    }

    @Test
    fun testEncodeTransferFrom() {
        val from = "0xC900BD2233B9596E5589446CC494c1EE70D12F67"
        val to = "0x3fC91A3afd70395Cd496C647d5a6CC9D4B2b7FAD"
        val tokenId = 12345

        val parameters = listOf(
            Parameter("from", "address", from),
            Parameter("to", "address", to),
            Parameter("tokenId", "uint256", tokenId.toString())
        )

        val evmServiceEncodedTransaction = transactionEncoder.encode("safeTransferFrom", parameters)
        val web3jFunction = FunctionEncoder.makeFunction(
            "safeTransferFrom",
            listOf("address", "address", "uint256"),
            listOf(from, to, tokenId.toBigInteger()),
            emptyList()
        )

        val web3jEncodedTransaction = FunctionEncoder.encode(web3jFunction)


        assertThat(evmServiceEncodedTransaction).isEqualTo(web3jEncodedTransaction)
    }

    @Test
    fun testEncodeApprove() {
        val spender = "0xC900BD2233B9596E5589446CC494c1EE70D12F67"
        val tokenId = 54321

        val parameters = listOf(
            Parameter("spender", "address", spender),
            Parameter("tokenId", "uint256", tokenId.toString())
        )

        val evmServiceEncodedTransaction = transactionEncoder.encode("approve", parameters)
        val web3jFunction = FunctionEncoder.makeFunction(
            "approve",
            listOf("address", "uint256"),
            listOf(spender, tokenId.toBigInteger()),
            emptyList()
        )

        val web3jEncodedTransaction = FunctionEncoder.encode(web3jFunction)

        assertThat(evmServiceEncodedTransaction).isEqualTo(web3jEncodedTransaction)
    }

    @Test
    fun testEncodeBalanceOfCall() {
        val owner = "0xC900BD2233B9596E5589446CC494c1EE70D12F67"

        val parameters = listOf(
            Parameter("owner", "address", owner)
        )

        val evmServiceEncodedTransaction = transactionEncoder.encode("balanceOf", parameters)
        val web3jFunction = FunctionEncoder.makeFunction(
            "balanceOf",
            listOf("address"),
            listOf(owner),
            listOf("uint256")
        )

        val web3jEncodedTransaction = FunctionEncoder.encode(web3jFunction)

        assertThat(evmServiceEncodedTransaction).isEqualTo(web3jEncodedTransaction)
    }



    @Test
    fun testArrayValue() {
        val owner = "[1,2,3,4,5,6,7,8,9,10]"
        val web3jInput = listOf(1,2,3,4,5,6,7,8,9,10)


        val parameters = listOf(
            Parameter("_array", "uint256[]", owner)
        )

        val evmServiceEncodedTransaction = transactionEncoder.encode("setIntArray", parameters)
        println(evmServiceEncodedTransaction)
        val web3jFunction = FunctionEncoder.makeFunction(
            "setIntArray",
            listOf("uint256[]"),
            listOf(web3jInput),
            emptyList()
        )

        val web3jEncodedTransaction = FunctionEncoder.encode(web3jFunction)

        assertThat(evmServiceEncodedTransaction).isEqualTo(web3jEncodedTransaction)
    }






}
