package com.r3.corda.demo.interop.evm

import java.math.BigInteger
import net.corda.v5.application.interop.evm.EvmService
import net.corda.v5.application.interop.evm.Parameter
import net.corda.v5.application.interop.evm.Type
import net.corda.v5.application.interop.evm.options.CallOptions
import net.corda.v5.application.interop.evm.options.EvmOptions
import net.corda.v5.application.interop.evm.options.TransactionOptions

open class ERC1155Impl(
    private val evmService: EvmService,
    private val evmOptions: EvmOptions,
) : ERC1155 {

    override fun balanceOf(account: String, id: BigInteger): BigInteger {
        return evmService.call(
            "balanceOf",
            "to",
            CallOptions("latest", evmOptions),
            Type.UINT256,
            Parameter.of("account", Type.ADDRESS, account),
            Parameter.of("id", Type.UINT256, id),
        )
    }

    override fun balanceOfBatch(accounts: List<String>, ids: List<BigInteger>): List<BigInteger> {
        return evmService.call(
            "balanceOfBatch",
            "to",
            CallOptions("latest", evmOptions),
            Type.UINT256_LIST,
            Parameter.of("accounts", Type.ADDRESS_LIST, accounts),
            Parameter.of("ids", Type.UINT256_LIST, ids),
        )
    }

    override fun setApprovalForAll(operator: String, approved: Boolean): String {
        return evmService.transaction(
            "setApprovalForAll",
            "to",
            TransactionOptions(evmOptions, BigInteger.ONE, BigInteger.ONE, BigInteger.ONE, BigInteger.ONE),
            Parameter.of("operator", Type.ADDRESS, operator),
            Parameter.of("id", Type.BOOLEAN, approved),
        )
    }

    override fun isApprovedForAll(account: String, operator: String): Boolean {
        return evmService.call(
            "isApprovedForAll",
            "to",
            CallOptions("latest", evmOptions),
            Type.BOOLEAN,
            Parameter.of("account", Type.ADDRESS, account),
            Parameter.of("operator", Type.ADDRESS, operator),
        )
    }

    override fun safeTransferFrom(from: String, to: String, id: BigInteger, amount: BigInteger, data: String): String {
        return evmService.transaction(
            "safeTransferFrom",
            "to",
            TransactionOptions(evmOptions, BigInteger.ONE, BigInteger.ONE, BigInteger.ONE, BigInteger.ONE),
            Parameter.of("from", Type.ADDRESS, from),
            Parameter.of("to", Type.ADDRESS, to),
            Parameter.of("id", Type.UINT256, id),
            Parameter.of("amount", Type.UINT256, amount),
            Parameter.of("data", Type.STRING, data),
        )
    }

    override fun safeBatchTransferFrom(from: String, to: String, ids: List<BigInteger>, amounts: List<BigInteger>, data: String): String {
        return evmService.transaction(
            "safeTransferFrom",
            "to",
            TransactionOptions(evmOptions, BigInteger.ONE, BigInteger.ONE, BigInteger.ONE, BigInteger.ONE),
            Parameter.of("from", Type.ADDRESS, from),
            Parameter.of("to", Type.ADDRESS, to),
            Parameter.of("id", Type.UINT256_LIST, ids),
            Parameter.of("amount", Type.UINT256_LIST, amounts),
            Parameter.of("data", Type.STRING, data),
        )
    }
}