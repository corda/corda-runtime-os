package com.r3.corda.evmbridge.web3j

import java.math.BigInteger
import org.web3j.protocol.Web3j
import org.web3j.protocol.core.DefaultBlockParameter
import org.web3j.protocol.core.methods.response.EthGetCode
import org.web3j.protocol.core.methods.response.EthSendTransaction
import org.web3j.service.TxSignService
import org.web3j.tx.TransactionManager

class DelegatingTransactionManager(val web3j: Web3j, val txSigningService: TxSignService, val chainId: Long, val forwarder: EvmForwarder) : TransactionManager(web3j, txSigningService.address) {

    override fun sendTransaction(
        gasPrice: BigInteger?,
        gasLimit: BigInteger?,
        to: String?,
        data: String?,
        value: BigInteger?,
        constructor: Boolean
    ): EthSendTransaction {
        TODO("Not yet implemented")
    }

    override fun sendEIP1559Transaction(
        chainId: Long,
        maxPriorityFeePerGas: BigInteger?,
        maxFeePerGas: BigInteger?,
        gasLimit: BigInteger?,
        to: String?,
        data: String?,
        value: BigInteger?,
        constructor: Boolean
    ): EthSendTransaction {
        TODO("Not yet implemented")
    }

    override fun sendCall(to: String?, data: String?, defaultBlockParameter: DefaultBlockParameter?): String {
        return forwarder.transactionManager.sendCall(to, data, defaultBlockParameter)
    }

    override fun getCode(contractAddress: String?, defaultBlockParameter: DefaultBlockParameter?): EthGetCode {
        TODO("Not yet implemented")
    }
}