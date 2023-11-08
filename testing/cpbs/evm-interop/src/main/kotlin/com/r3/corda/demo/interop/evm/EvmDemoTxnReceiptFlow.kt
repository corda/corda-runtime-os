package com.r3.corda.demo.interop.evm

import net.corda.v5.application.flows.ClientRequestBody
import net.corda.v5.application.flows.ClientStartableFlow
import net.corda.v5.application.flows.CordaInject
import net.corda.v5.application.interop.evm.EvmService
import net.corda.v5.application.interop.evm.options.EvmOptions
import net.corda.v5.application.marshalling.JsonMarshallingService
import net.corda.v5.base.annotations.Suspendable
import org.slf4j.LoggerFactory

/**
 * The Evm Demo Flow is solely for demoing access to the EVM from Corda.
 */
@Suppress("unused")
class EvmDemoTxnReceiptFlow : ClientStartableFlow {

    private companion object {
        private val log = LoggerFactory.getLogger(this::class.java.enclosingClass)
    }

    @CordaInject
    lateinit var jsonMarshallingService: JsonMarshallingService

    @CordaInject
    lateinit var evmService: EvmService

    @Suspendable
    override fun call(requestBody: ClientRequestBody): String {
        log.info("Starting Evm Demo Txn Receipt Flow...")
        try {
            // Get any of the relevant details from the request here
            val inputs = requestBody.getRequestBodyAs(jsonMarshallingService, EvmDemoTxnReceiptInput::class.java)
            val options = EvmOptions(inputs.rpcUrl!!, "")

            log.info("Querying Transaction Receipt for ${inputs.hash} ...")
            val receipt = evmService.getTransactionReceipt(inputs.hash!!, options)
            return jsonMarshallingService.format(receipt)
        } catch (e: Exception) {
            log.error("Unexpected error while processing the flow", e)
            throw e
        }
    }
}

