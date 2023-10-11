package net.corda.interop.evm.dispatcher.factory

import net.corda.interop.evm.dispatcher.EvmDispatcher
import net.corda.interop.evm.EthereumConnector


/**
 * Dispatcher Factory Defines the methods that must be implemented by any of the EVM Dispatchers
 */
interface DispatcherFactory {
    /**
     * Dispatcher used to make a call on an EVM Node
     *
     * @param evmConnector The evmConnector class used to make rpc calls to the node
     */
    fun callDispatcher(evmConnector: EthereumConnector): EvmDispatcher

    /**
     * Dispatcher used to get the transaction receipt on an EVM Node
     *
     * @param evmConnector The evmConnector class used to make rpc calls to the node
     */
    fun getTransactionByReceiptDispatcher(evmConnector: EthereumConnector): EvmDispatcher


    /**
     * Dispatcher used to send a transaction on an EVM Node
     *
     * @param evmConnector The evmConnector class used to make rpc calls to the node
     */
    fun sendRawTransactionDispatcher(evmConnector: EthereumConnector): EvmDispatcher

}