package net.corda.web3j.dispatcher.factory

import net.corda.web3j.dispatcher.EvmDispatcher
import net.corda.web3j.EthereumConnector
import net.corda.web3j.dispatcher.CallDispatcher
import net.corda.web3j.dispatcher.GetTransactionReceiptDispatcher
import net.corda.web3j.dispatcher.SendRawTransactionDispatcher

object GenericDispatcherFactory : DispatcherFactory {


    override fun callDispatcher(evmConnector: EthereumConnector): EvmDispatcher {
        return CallDispatcher(evmConnector)
    }


    override fun getTransactionByReceiptDispatcher(evmConnector: EthereumConnector): EvmDispatcher {
        return GetTransactionReceiptDispatcher(evmConnector)
    }


    override fun sendRawTransactionDispatcher(evmConnector: EthereumConnector): EvmDispatcher {
        return SendRawTransactionDispatcher(evmConnector)
    }

}
