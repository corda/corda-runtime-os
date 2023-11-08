package com.r3.corda.demo.interop.evm

import java.math.BigInteger
import net.corda.v5.application.interop.evm.EvmService
import net.corda.v5.application.interop.evm.Parameter
import net.corda.v5.application.interop.evm.Type
import net.corda.v5.application.interop.evm.options.EvmOptions
import net.corda.v5.application.interop.evm.options.TransactionOptions

class FractionalOwnershipToken(
    private val evmService: EvmService,
    private val evmOptions: EvmOptions,
): ERC1155Impl(evmService, evmOptions) {
    fun sendTokenOne(from: String, to: String, amount: BigInteger): String {
        return evmService.transaction(
            "sendTokenOne",
            "to",
            TransactionOptions(evmOptions, BigInteger.ONE, BigInteger.ONE, BigInteger.ONE, BigInteger.ONE),
            Parameter.of("operator", Type.ADDRESS, from),
            Parameter.of("to", Type.ADDRESS, to),
            Parameter.of("amount", Type.UINT256, amount),
        )
    }
}