package com.r3.corda.demo.interop.evm

import net.corda.v5.application.interop.evm.EvmService
import net.corda.v5.application.interop.evm.Parameter
import net.corda.v5.application.interop.evm.Type
import net.corda.v5.application.interop.evm.options.CallOptions
import net.corda.v5.application.interop.evm.options.EvmOptions
import net.corda.v5.application.interop.evm.options.TransactionOptions
import net.corda.v5.base.annotations.Suspendable
import java.math.BigInteger

class ERC1155Contract(private val rpcUrl: String, private val evmService: EvmService, private val contractAddress: String) {
    @Suspendable
    fun safeTransferFrom(from: String, to: String, amount: BigInteger): String {
        val dummyGasNumber = BigInteger("a41c5", 16)
        val transactionOptions = TransactionOptions(
            dummyGasNumber,                 // gasLimit
            0.toBigInteger(),               // value
            20000000000.toBigInteger(),     // maxFeePerGas
            20000000000.toBigInteger(),     // maxPriorityFeePerGas
            this.rpcUrl,                // rpcUrl
            from,          // from
        )

        val parameters = listOf(
            Parameter.of("from", Type.ADDRESS, from),
            Parameter.of("to", Type.ADDRESS, to),
            Parameter.of("amount", Type.UINT256, amount),
            Parameter.of("data", Type.BYTES, ""),
        )

        return this.evmService.transaction(
            "safeTransferFrom",
            contractAddress,
            transactionOptions,
            parameters
        )
    }


    fun safeBatchTransferFrom(from: String, to: String, amount: BigInteger, data: String): String {
        val dummyGasNumber = BigInteger("a41c5", 16)
        val transactionOptions = TransactionOptions(
            dummyGasNumber,                 // gasLimit
            0.toBigInteger(),               // value
            20000000000.toBigInteger(),     // maxFeePerGas
            20000000000.toBigInteger(),     // maxPriorityFeePerGas
            this.rpcUrl,                // rpcUrl
            from,          // from
        )

        val parameters = listOf(
            Parameter.of("_from", Type.ADDRESS, from),
            Parameter.of("_to", Type.ADDRESS, to),
            Parameter.of("_amount", Type.UINT256, amount),
            Parameter.of("data", Type.BYTE_ARRAY, data),
        )

        return this.evmService.transaction(
            "safeBatchTransferFrom",
            contractAddress,
            transactionOptions,
            parameters
        )
    }

    fun balanceOf(owner: String, id: BigInteger): BigInteger {
        val options = CallOptions(EvmOptions(this.rpcUrl, ""))

        return this.evmService.call(
            "balanceOf", this.contractAddress, options, Type.UINT256, listOf(
                Parameter.of("from", Type.ADDRESS, owner),
                Parameter.of("id", Type.UINT256, id)
            )
        )

    }

    fun balanceOfBatch(owners: List<String>, ids: List<BigInteger>): List<BigInteger> {
        val options = CallOptions(EvmOptions(this.rpcUrl, ""))
        return this.evmService.call(
            "balanceOfBatch", this.contractAddress, options, Type.UINT256_LIST, listOf(
                Parameter.of("from", Type.ADDRESS_ARRAY, owners),
                Parameter.of("id", Type.UINT256_ARRAY, ids)
            )
        )

    }

    fun setApprovalForAll(from: String, operator: String, approved: Boolean): String {
        val dummyGasNumber = BigInteger("a41c5", 16)
        val transactionOptions = TransactionOptions(
            dummyGasNumber,                 // gasLimit
            0.toBigInteger(),               // value
            20000000000.toBigInteger(),     // maxFeePerGas
            20000000000.toBigInteger(),     // maxPriorityFeePerGas
            this.rpcUrl,                // rpcUrl
            from,          // from
        )

        val parameters = listOf(
            Parameter.of("_operator", Type.ADDRESS, operator),
            Parameter.of("_approved", Type.BOOLEAN, approved),
        )

        return evmService.transaction(
            "setApprovalForAll",
            contractAddress,
            transactionOptions,
            parameters
        )
    }

    fun isApprovedForAll(owner: String, operator: String): Boolean {
        val options = CallOptions(EvmOptions(this.rpcUrl, ""))

        return this.evmService.call(
            "isApprovedForAll", this.contractAddress, options, Type.BOOLEAN, listOf(
                Parameter.of("_owner", Type.ADDRESS, owner),
                Parameter.of("_operator", Type.ADDRESS, operator)
            )
        )
    }
}