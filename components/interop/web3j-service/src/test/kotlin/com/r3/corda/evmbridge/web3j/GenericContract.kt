package com.r3.corda.evmbridge.web3j

import org.web3j.abi.datatypes.Type
import org.web3j.crypto.Credentials
import org.web3j.protocol.Web3j
import org.web3j.protocol.core.RemoteFunctionCall
import org.web3j.protocol.core.methods.response.TransactionReceipt
import org.web3j.tx.Contract
import org.web3j.tx.gas.DefaultGasProvider

// TODO: support all parent constructors
class GenericContract(
    contractBinary: String,
    contractAddress: String,
    web3j: Web3j,
    credentials: Credentials
) : Contract(contractBinary, contractAddress, web3j, credentials, DefaultGasProvider()) {

    fun remoteFunctionCallSingleResult(function: ContractFunction): RemoteFunctionCall<*> {
        val fn = function.generateWeb3JFunction()
        val returnType = getReturnType(function.outputs)
        return executeRemoteCallSingleValueReturn(fn, returnType.single())
    }

    fun remoteFunctionCallMultipleResult(function: ContractFunction): RemoteFunctionCall<List<Type<*>>> {
        val fn = function.generateWeb3JFunction()
        return executeRemoteCallMultipleValueReturn(fn)
    }

    fun remoteCallTransaction(function: ContractFunction): RemoteFunctionCall<TransactionReceipt> {
        val fn = function.generateWeb3JFunction()
        return executeRemoteCallTransaction(fn)
    }

    fun callFunction(function: ContractFunction): RemoteFunctionCall<*> {
        return if (function.transaction) {
            remoteCallTransaction(function)
        } else {
            if (function.outputs.size > 1) {
                remoteFunctionCallMultipleResult(function)
            } else {
                remoteFunctionCallSingleResult(function)
            }
        }
    }
}