package com.r3.corda.demo.interop.evm

import java.math.BigInteger

interface ERC1155 {
    fun balanceOf(account: String, id: BigInteger): BigInteger
    fun balanceOfBatch(accounts: List<String>, ids: List<BigInteger>): List<BigInteger>
    fun setApprovalForAll(operator: String, approved: Boolean): String
    fun isApprovedForAll(account: String, operator: String): Boolean
    fun safeTransferFrom(
        from: String,
        to: String,
        id: BigInteger,
        amount: BigInteger,
        data: String
    ): String
    fun safeBatchTransferFrom(
        from: String,
        to: String,
        ids: List<BigInteger>,
        amounts: List<BigInteger>,
        data: String
    ): String
}