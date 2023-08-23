package net.corda.web3j.dispatcher

import net.corda.data.interop.evm.EvmRequest
import net.corda.data.interop.evm.EvmResponse

/**
 * Defines the dispatch method that an EVM Dispatcher class must implement
 */
interface EvmDispatcher {
    /**
     * Send an EVMRequest and retrieves an EVMResponse
     *  @param evmRequest is an EVM Request defined in Corda API
     */
    fun dispatch(evmRequest: EvmRequest): EvmResponse
}
