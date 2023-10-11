package net.corda.interop.evm.dispatcher.factory

import net.corda.interop.evm.dispatcher.EvmDispatcher
import net.corda.interop.evm.EthereumConnector
import net.corda.interop.evm.dispatcher.CallDispatcher
import net.corda.interop.evm.dispatcher.GetTransactionReceiptDispatcher
import net.corda.interop.evm.dispatcher.SendRawTransactionDispatcher

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
